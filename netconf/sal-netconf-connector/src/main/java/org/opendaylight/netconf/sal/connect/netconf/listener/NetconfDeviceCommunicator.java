/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceCommunicator implements NetconfClientSessionListener, RemoteDeviceCommunicator<NetconfMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceCommunicator.class);

    protected final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice;
    private final Optional<UserPreferences> overrideNetconfCapabilities;
    protected final RemoteDeviceId id;
    private final Lock sessionLock = new ReentrantLock();

    private final Semaphore semaphore;
    private final int concurentRpcMsgs;

    private final Queue<Request> requests = new ArrayDeque<>();
    private NetconfClientSession session;

    private Future<?> initFuture;
    private final SettableFuture<NetconfDeviceCapabilities> firstConnectionFuture;

    // isSessionClosing indicates a close operation on the session is issued and
    // tearDown will surely be called later to finish the close.
    // Used to allow only one thread to enter tearDown and other threads should
    // NOT enter it simultaneously and should end its close operation without
    // calling tearDown to release the locks they hold to avoid deadlock.
    private final AtomicBoolean isSessionClosing = new AtomicBoolean(false);

    public Boolean isSessionClosing() {
        return isSessionClosing.get();
    }

    public NetconfDeviceCommunicator(final RemoteDeviceId id, final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice,
            final UserPreferences NetconfSessionPreferences, final int rpcMessageLimit) {
        this(id, remoteDevice, Optional.of(NetconfSessionPreferences), rpcMessageLimit);
    }

    public NetconfDeviceCommunicator(final RemoteDeviceId id,
                                     final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice,
                                     final int rpcMessageLimit) {
        this(id, remoteDevice, Optional.<UserPreferences>absent(), rpcMessageLimit);
    }

    private NetconfDeviceCommunicator(final RemoteDeviceId id, final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice,
                                      final Optional<UserPreferences> overrideNetconfCapabilities, final int rpcMessageLimit) {
        this.concurentRpcMsgs = rpcMessageLimit;
        this.id = id;
        this.remoteDevice = remoteDevice;
        this.overrideNetconfCapabilities = overrideNetconfCapabilities;
        this.firstConnectionFuture = SettableFuture.create();
        this.semaphore = rpcMessageLimit > 0 ? new Semaphore(rpcMessageLimit) : null;
    }

    @Override
    public void onSessionUp(final NetconfClientSession session) {
        sessionLock.lock();
        try {
            LOG.debug("{}: Session established", id);
            this.session = session;

            NetconfSessionPreferences netconfSessionPreferences =
                                             NetconfSessionPreferences.fromNetconfSession(session);
            LOG.trace("{}: Session advertised capabilities: {}", id,
                    netconfSessionPreferences);

            if(overrideNetconfCapabilities.isPresent()) {
                netconfSessionPreferences = overrideNetconfCapabilities.get().isOverride() ?
                        netconfSessionPreferences.replaceModuleCaps(overrideNetconfCapabilities.get().getSessionPreferences()) :
                        netconfSessionPreferences.addModuleCaps(overrideNetconfCapabilities.get().getSessionPreferences());
                LOG.debug(
                        "{}: Session capabilities overridden, capabilities that will be used: {}",
                        id, netconfSessionPreferences);
            }


            remoteDevice.onRemoteSessionUp(netconfSessionPreferences, this);
            if (!firstConnectionFuture.isDone()) {
                firstConnectionFuture.set(netconfSessionPreferences.getNetconfDeviceCapabilities());
            }
        }
        finally {
            sessionLock.unlock();
        }
    }

    /**
     *
     * @param dispatcher
     * @param config
     * @return future that returns succes on first succesfull connection and failure when the underlying
     * reconnecting strategy runs out of reconnection attempts
     */
    public ListenableFuture<NetconfDeviceCapabilities> initializeRemoteConnection(final NetconfClientDispatcher dispatcher, final NetconfClientConfiguration config) {
        if(config instanceof NetconfReconnectingClientConfiguration) {
            initFuture = dispatcher.createReconnectingClient((NetconfReconnectingClientConfiguration) config);
        } else if(config instanceof NetconfReversedClientConfiguration){
            initFuture = dispatch.createReversedClient((NetconfReversedClientConfiguration) config);
        } else {
            initFuture = dispatcher.createClient(config);
        }


        initFuture.addListener(new GenericFutureListener<Future<Object>>(){

            @Override
            public void operationComplete(final Future<Object> future) throws Exception {
                if (!future.isSuccess() && !future.isCancelled()) {
                    LOG.debug("{}: Connection failed", id, future.cause());
                    NetconfDeviceCommunicator.this.remoteDevice.onRemoteSessionFailed(future.cause());
                    if (firstConnectionFuture.isDone()) {
                        firstConnectionFuture.setException(future.cause());
                    }
                }
            }
        });
        return firstConnectionFuture;
    }

    public void disconnect() {
        // If session is already in closing, no need to close it again
        if(session != null && isSessionClosing.compareAndSet(false, true)) {
            session.close();
        }
    }

    private void tearDown(final String reason) {
        if (!isSessionClosing()) {
            LOG.warn("It's curious that no one to close the session but tearDown is called!");
        }
        LOG.debug("Tearing down {}", reason);
        final List<UncancellableFuture<RpcResult<NetconfMessage>>> futuresToCancel = Lists.newArrayList();
        sessionLock.lock();
        try {
            if( session != null ) {
                session = null;

                /*
                 * Walk all requests, check if they have been executing
                 * or cancelled and remove them from the queue.
                 */
                final Iterator<Request> it = requests.iterator();
                while (it.hasNext()) {
                    final Request r = it.next();
                    if (r.future.isUncancellable()) {
                        futuresToCancel.add( r.future );
                        it.remove();
                    } else if (r.future.isCancelled()) {
                        // This just does some house-cleaning
                        it.remove();
                    }
                }

                remoteDevice.onRemoteSessionDown();
            }
        }
        finally {
            sessionLock.unlock();
        }

        // Notify pending request futures outside of the sessionLock to avoid unnecessarily
        // blocking the caller.
        for (final UncancellableFuture<RpcResult<NetconfMessage>> future : futuresToCancel) {
            if( Strings.isNullOrEmpty( reason ) ) {
                future.set( createSessionDownRpcResult() );
            } else {
                future.set( createErrorRpcResult( RpcError.ErrorType.TRANSPORT, reason ) );
            }
        }

        isSessionClosing.set(false);
    }

    private RpcResult<NetconfMessage> createSessionDownRpcResult() {
        return createErrorRpcResult( RpcError.ErrorType.TRANSPORT,
                             String.format( "The netconf session to %1$s is disconnected", id.getName() ) );
    }

    private RpcResult<NetconfMessage> createErrorRpcResult(final RpcError.ErrorType errorType, final String message) {
        return RpcResultBuilder.<NetconfMessage>failed()
                .withError(errorType, NetconfDocumentedException.ErrorTag.OPERATION_FAILED.getTagValue(), message).build();
    }

    @Override
    public void onSessionDown(final NetconfClientSession session, final Exception e) {
        // If session is already in closing, no need to call tearDown again.
        if (isSessionClosing.compareAndSet(false, true)) {
            LOG.warn("{}: Session went down", id, e);
            tearDown( null );
        }
    }

    @Override
    public void onSessionTerminated(final NetconfClientSession session, final NetconfTerminationReason reason) {
        // onSessionTerminated is called directly by disconnect, no need to compare and set isSessionClosing.
        LOG.warn("{}: Session terminated {}", id, reason);
        tearDown( reason.getErrorMessage() );
    }

    @Override
    public void close() {
        // Cancel reconnect if in progress
        if(initFuture != null) {
            initFuture.cancel(false);
        }
        // Disconnect from device
        // tear down not necessary, called indirectly by the close in disconnect()
        disconnect();
    }

    @Override
    public void onMessage(final NetconfClientSession session, final NetconfMessage message) {
        /*
         * Dispatch between notifications and messages. Messages need to be processed
         * with lock held, notifications do not.
         */
        if (isNotification(message)) {
            processNotification(message);
        } else {
            processMessage(message);
        }
    }

    private void processMessage(final NetconfMessage message) {
        Request request = null;
        sessionLock.lock();

        try {
            request = requests.peek();
            if (request != null && request.future.isUncancellable()) {
                requests.poll();
                // we have just removed one request from the queue
                // we can also release one permit
                if(semaphore != null) {
                    semaphore.release();
                }
            } else {
                request = null;
                LOG.warn("{}: Ignoring unsolicited message {}", id,
                        msgToS(message));
            }
        }
        finally {
            sessionLock.unlock();
        }

        if( request != null ) {

            LOG.debug("{}: Message received {}", id, message);

            if(LOG.isTraceEnabled()) {
                LOG.trace( "{}: Matched request: {} to response: {}", id, msgToS( request.request ), msgToS( message ) );
            }

            try {
                NetconfMessageTransformUtil.checkValidReply( request.request, message );
            } catch (final NetconfDocumentedException e) {
                LOG.warn(
                        "{}: Invalid request-reply match, reply message contains different message-id, request: {}, response: {}",
                        id, msgToS(request.request), msgToS(message), e);

                request.future.set( RpcResultBuilder.<NetconfMessage>failed()
                        .withRpcError( NetconfMessageTransformUtil.toRpcError( e ) ).build() );

                //recursively processing message to eventually find matching request
                processMessage(message);

                return;
            }

            try {
                NetconfMessageTransformUtil.checkSuccessReply(message);
            } catch(final NetconfDocumentedException e) {
                LOG.warn(
                        "{}: Error reply from remote device, request: {}, response: {}",
                        id, msgToS(request.request), msgToS(message), e);

                request.future.set( RpcResultBuilder.<NetconfMessage>failed()
                        .withRpcError( NetconfMessageTransformUtil.toRpcError( e ) ).build() );
                return;
            }

            request.future.set( RpcResultBuilder.success( message ).build() );
        }
    }

    private static String msgToS(final NetconfMessage msg) {
        return XmlUtil.toString(msg.getDocument());
    }

    @Override
    public ListenableFuture<RpcResult<NetconfMessage>> sendRequest(final NetconfMessage message, final QName rpc) {
        sessionLock.lock();

        if (semaphore != null && !semaphore.tryAcquire()) {
            LOG.warn("Limit of concurrent rpc messages was reached (limit :" +
                    concurentRpcMsgs + "). Rpc reply message is needed. Discarding request of Netconf device with id" + id.getName());
            sessionLock.unlock();
            return Futures.immediateFailedFuture(new NetconfDocumentedException("Limit of rpc messages was reached (Limit :" +
                    concurentRpcMsgs + ") waiting for emptying the queue of Netconf device with id" + id.getName()));
        }

        try {
            return sendRequestWithLock(message, rpc);
        } finally {
            sessionLock.unlock();
        }
    }

    private ListenableFuture<RpcResult<NetconfMessage>> sendRequestWithLock(
                                               final NetconfMessage message, final QName rpc) {
        if(LOG.isTraceEnabled()) {
            LOG.trace("{}: Sending message {}", id, msgToS(message));
        }

        if (session == null) {
            LOG.warn("{}: Session is disconnected, failing RPC request {}",
                    id, message);
            return Futures.immediateFuture( createSessionDownRpcResult() );
        }

        final Request req = new Request( new UncancellableFuture<RpcResult<NetconfMessage>>(true),
                                         message );
        requests.add(req);

        session.sendMessage(req.request).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(final Future<Void> future) throws Exception {
                if( !future.isSuccess() ) {
                    // We expect that a session down will occur at this point
                    LOG.debug("{}: Failed to send request {}", id,
                            XmlUtil.toString(req.request.getDocument()),
                            future.cause());

                    if( future.cause() != null ) {
                        req.future.set( createErrorRpcResult( RpcError.ErrorType.TRANSPORT,
                                                              future.cause().getLocalizedMessage() ) );
                    } else {
                        req.future.set( createSessionDownRpcResult() ); // assume session is down
                    }
                    req.future.setException( future.cause() );
                }
                else {
                    LOG.trace("Finished sending request {}", req.request);
                }
            }
        });

        return req.future;
    }

    private void processNotification(final NetconfMessage notification) {
        if(LOG.isTraceEnabled()) {
            LOG.trace("{}: Notification received: {}", id, notification);
        }

        remoteDevice.onNotification(notification);
    }

    private static boolean isNotification(final NetconfMessage message) {
        final XmlElement xmle = XmlElement.fromDomDocument(message.getDocument());
        return XmlNetconfConstants.NOTIFICATION_ELEMENT_NAME.equals(xmle.getName()) ;
    }

    private static final class Request {
        final UncancellableFuture<RpcResult<NetconfMessage>> future;
        final NetconfMessage request;

        private Request(final UncancellableFuture<RpcResult<NetconfMessage>> future,
                        final NetconfMessage request) {
            this.future = future;
            this.request = request;
        }
    }
}

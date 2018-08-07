/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.annotation.concurrent.GuardedBy;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNetconfClientSessionListener implements NetconfClientSessionListener {
    @Override
    public void onSessionIdle() {
        LOG.info("session idle");
    }

    private static final class RequestEntry {
        private final Promise<NetconfMessage> promise;
        private final NetconfMessage request;

        RequestEntry(Promise<NetconfMessage> future, NetconfMessage request) {
            this.promise = Preconditions.checkNotNull(future);
            this.request = Preconditions.checkNotNull(request);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNetconfClientSessionListener.class);

    @GuardedBy("this")
    private final Queue<RequestEntry> requests = new ArrayDeque<>();

    @GuardedBy("this")
    private NetconfClientSession clientSession;

    @GuardedBy("this")
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    private void dispatchRequest() {
        while (!requests.isEmpty()) {
            final RequestEntry e = requests.peek();
            if (e.promise.setUncancellable()) {
                LOG.debug("Sending message {}", e.request);
                clientSession.sendMessage(e.request);
                break;
            }

            LOG.debug("Message {} has been cancelled, skipping it", e.request);
            requests.poll();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final synchronized void onSessionUp(NetconfClientSession clientSession) {
        this.clientSession = Preconditions.checkNotNull(clientSession);
        LOG.debug("Client session {} went up", clientSession);
        dispatchRequest();
    }

    private synchronized void tearDown(final Exception cause) {
        final RequestEntry e = requests.poll();
        if (e != null) {
            e.promise.setFailure(cause);
        }

        this.clientSession = null;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final void onSessionDown(NetconfClientSession clientSession, Exception exception) {
        LOG.debug("Client Session {} went down unexpectedly", clientSession, exception);
        tearDown(exception);
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final void onSessionTerminated(NetconfClientSession clientSession,
                                          NetconfTerminationReason netconfTerminationReason) {
        LOG.debug("Client Session {} terminated, reason: {}", clientSession,
                netconfTerminationReason.getErrorMessage());
        tearDown(new RuntimeException(netconfTerminationReason.getErrorMessage()));
    }

    @Override
    public synchronized void onMessage(NetconfClientSession session, NetconfMessage message) {
        LOG.debug("New message arrived: {}", message);

        final RequestEntry e = requests.poll();
        if (e != null) {
            e.promise.setSuccess(message);
            dispatchRequest();
        } else {
            LOG.info("Ignoring unsolicited message {}", message);
        }
    }

    public final synchronized Future<NetconfMessage> sendRequest(NetconfMessage message) {
        final RequestEntry req = new RequestEntry(GlobalEventExecutor.INSTANCE.newPromise(), message);

        requests.add(req);
        if (clientSession != null) {
            dispatchRequest();
        }

        return req.promise;
    }
}

/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import com.google.common.base.Strings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-socket session handler that is responsible for controlling of session, managing subscription
 * to data-change-event or notification listener, and sending of data over established web-socket session.
 */
@WebSocket
public class WebSocketSessionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessionHandler.class);
    private static final ByteBuffer PING_PAYLOAD = ByteBuffer.wrap("ping".getBytes(Charset.defaultCharset()));

    private final ScheduledExecutorService executorService;
    private final BaseListenerInterface listener;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    private Session session;
    private ScheduledFuture<?> pingProcess;

    /**
     * Creation of the new web-socket session handler.
     *
     * @param executorService       Executor that is used for periodical sending of web-socket ping messages to keep
     *                              session up even if the notifications doesn't flow from server to clients or clients
     *                              don't implement ping-pong service.
     * @param listener              YANG notification or data-change event listener to which client on this web-socket
     *                              session subscribes to.
     * @param maximumFragmentLength Maximum fragment length in number of bytes. If this parameter is set to 0,
     *                              the maximum fragment length is disabled and messages up to 64 KB can be sent
     *                              in TCP segment (exceeded notification length ends in error). If the parameter
     *                              is set to non-zero positive value, messages longer than this parameter are
     *                              fragmented into multiple web-socket messages sent in one transaction.
     * @param heartbeatInterval     Interval in milliseconds of sending of ping control frames to remote endpoint
     *                              to keep session up. Ping control frames are disabled if this parameter is set to 0.
     */
    WebSocketSessionHandler(final ScheduledExecutorService executorService, final BaseListenerInterface listener,
            final int maximumFragmentLength, final int heartbeatInterval) {
        this.executorService = executorService;
        this.listener = listener;
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Handling of the web-socket connected event (web-socket session between local server and remote endpoint has been
     * established). Web-socket session handler is registered at data-change-event / YANG notification listener and
     * the heartbeat ping process is started if it is enabled.
     *
     * @param webSocketSession Created web-socket session.
     * @see OnWebSocketConnect More information about invocation of this method and parameters.
     */
    @OnWebSocketConnect
    public synchronized void onWebSocketConnected(final Session webSocketSession) {
        if (webSocketSession == null || !webSocketSession.isOpen()) {
            this.session = webSocketSession;
            listener.addSubscriber(this);
            LOG.debug("A new web-socket session {} has been successfully registered.", webSocketSession);
            if (heartbeatInterval != 0) {
                // sending of PING frame can be long if there is an error on web-socket - from this reason
                // the fixed-rate should not be used
                pingProcess = executorService.scheduleWithFixedDelay(this::sendPingMessage, heartbeatInterval,
                        heartbeatInterval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Handling of web-socket session closed event (timeout, error, or both parties closed session). Removal
     * of subscription at listener and stopping of the ping process.
     *
     * @param statusCode Web-socket status code.
     * @param reason     Reason, why the web-socket is closed (for example, reached timeout).
     * @see OnWebSocketClose More information about invocation of this method and parameters.
     */
    @OnWebSocketClose
    public synchronized void onWebSocketClosed(final int statusCode, final String reason) {
        // note: there is not guarantee that Session.isOpen() returns true - it is better to not check it here
        // using 'session != null && session.isOpen()'
        if (session != null) {
            LOG.debug("Web-socket session has been closed with status code {} and reason message: {}.",
                    statusCode, reason);
            listener.removeSubscriber(this);
            stopPingProcess();
        }
    }

    /**
     * Handling of error in web-socket implementation. Subscription at listener is removed, open session is closed
     * and ping process is stopped.
     *
     * @param error Error details.
     * @see OnWebSocketError More information about invocation of this method and parameters.
     */
    @OnWebSocketError
    public synchronized void onWebSocketError(final Throwable error) {
        LOG.warn("An error occurred on web-socket: ", error);
        if (session != null) {
            LOG.warn("Trying to close web-socket session {} gracefully after error.", session);
            listener.removeSubscriber(this);
            if (session.isOpen()) {
                session.close();
            }
            stopPingProcess();
        }
    }

    private void stopPingProcess() {
        if (pingProcess != null && !pingProcess.isDone() && !pingProcess.isCancelled()) {
            pingProcess.cancel(true);
        }
    }

    /**
     * Sensing of string message to remote endpoint of {@link org.eclipse.jetty.websocket.api.Session}. If the maximum
     * fragment length is set to non-zero positive value and input message exceeds this value, message is fragmented
     * to multiple message fragments which are send individually but still in one web-socket transaction.
     *
     * @param message Message data to be send over web-socket session.
     */
    public synchronized void sendDataMessage(final String message) {
        if (session != null && session.isOpen()) {
            if (!Strings.isNullOrEmpty(message)) {
                final RemoteEndpoint remoteEndpoint = session.getRemote();
                if (maximumFragmentLength == 0) {
                    sendDataMessage(message, remoteEndpoint);
                } else {
                    if (message.length() <= maximumFragmentLength) {
                        sendDataMessage(message, remoteEndpoint);
                    } else {
                        final List<String> fragments = splitMessageToFragments(message, maximumFragmentLength);
                        sendFragmentedMessage(fragments, remoteEndpoint);
                    }
                }
            }
        } else {
            if (!Strings.isNullOrEmpty(message)) {
                LOG.trace("Message with body '{}' is not sent because underlay web-socket session is not open.",
                        message);
            }
        }
    }

    private void sendDataMessage(final String message, final RemoteEndpoint remoteEndpoint) {
        try {
            remoteEndpoint.sendString(message);
            LOG.trace("Message with body '{}' has been successfully sent to remote endpoint {}.",
                    message, remoteEndpoint);
        } catch (IOException e) {
            LOG.warn("Cannot send message over web-socket session {}.", session, e);
        }
    }

    private void sendFragmentedMessage(final List<String> orderedFragments, final RemoteEndpoint remoteEndpoint) {
        try {
            for (int i = 0; i < orderedFragments.size(); i++) {
                if (i == orderedFragments.size() - 1) {
                    // it is the last fragment
                    remoteEndpoint.sendPartialString(orderedFragments.get(i), true);
                } else {
                    remoteEndpoint.sendPartialString(orderedFragments.get(i), false);
                }
                LOG.trace("Message fragment number {} with body '{}' has been successfully sent to remote endpoint {}.",
                        i, orderedFragments.get(i), remoteEndpoint);
            }
        } catch (IOException e) {
            LOG.warn("Cannot send message fragment over web-socket session {}. "
                    + "All other fragments of message are dropped too.", session, e);
        }
    }

    private synchronized void sendPingMessage() {
        try {
            Objects.requireNonNull(session).getRemote().sendPing(PING_PAYLOAD);
        } catch (IOException e) {
            LOG.warn("Cannot send ping message over web-socket session {}.", session, e);
        }
    }

    private static List<String> splitMessageToFragments(final String inputMessage, final int maximumFragmentLength) {
        final List<String> parts = new ArrayList<>();
        int length = inputMessage.length();
        for (int i = 0; i < length; i += maximumFragmentLength) {
            parts.add(inputMessage.substring(i, Math.min(length, i + maximumFragmentLength)));
        }
        return parts;
    }

    /**
     * Get remote endpoint address of the current web-socket session.
     *
     * @return If the session exists the {@link InetSocketAddress} wrapped in {@link Optional} is returned. Otherwise,
     *     {@link Optional#empty()} is returned.
     */
    public synchronized Optional<InetSocketAddress> getRemoteEndpointAddress() {
        if (session != null) {
            return Optional.of(session.getRemote().getInetSocketAddress());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get state of the underlay web-socket session.
     *
     * @return Returns {@code true} if the web-socket session exists and is open. Otherwise, {@code false} is returned.
     */
    public synchronized boolean isSessionOpen() {
        return session != null && session.isOpen();
    }
}
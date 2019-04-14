/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import com.google.common.base.MoreObjects;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web socket client handler that is responsible for starting of the web-socket session thread and waiting until
 * the session dies.
 */
class WebSocketClientHandler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketClientHandler.class);

    private final URI uri;
    private final int pingInterval;
    private final String pingMessage;
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Creation of the web-socket client handler.
     *
     * @param uri                      Full stream URI including schema.
     * @param pingInterval             Interval ath which the ping messages should be sent to remote server.
     * @param pingMessage              Text of the ping message.
     * @param scheduledExecutorService Ping service executor.
     */
    WebSocketClientHandler(final URI uri, final int pingInterval, final String pingMessage,
            final ScheduledExecutorService scheduledExecutorService) {
        this.uri = uri;
        this.pingInterval = pingInterval;
        this.pingMessage = pingMessage;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * Starting of the web-socket client handler by listening to the initialised web-socket session. Then, thread
     * is blocked until web-socket session will be closed gracefully or on error.
     */
    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void run() {
        final WebSocketClient webSocketClient = getWebSocketClient();
        WebSocketSessionHandler webSocketSessionHandler;
        if (pingInterval == 0) {
            webSocketSessionHandler = new WebSocketSessionHandler();
        } else {
            webSocketSessionHandler = new WebSocketPingSessionHandler(scheduledExecutorService, pingMessage,
                    pingInterval);
        }
        try {
            LOG.info("Starting of the web-socket client {}.", this);
            webSocketClient.start();
            final ClientUpgradeRequest request = new ClientUpgradeRequest();
            webSocketClient.connect(webSocketSessionHandler, uri, request);
            LOG.info("Web-socket client {} has been started successfully.", this);
            webSocketSessionHandler.awaitClose();
        } catch (final Exception e) {
            LOG.error("Cannot start web-socket client {}.", this, e);
        } finally {
            try {
                webSocketClient.stop();
                LOG.info("Web-socket client {} has been stopped.", this);
            } catch (final Exception e) {
                LOG.error("Cannot stop web-socket client {}.", this, e);
            }
        }
    }

    /**
     * Fetching of the web-socket client. It is extracted from the {@link WebSocketTlsClientHandler#run()} because
     * of the possible adjustments (for example, authentication) with which the web-socket client can be configured.
     *
     * @return A new instance of {@link WebSocketClient}.
     */
    WebSocketClient getWebSocketClient() {
        return new WebSocketClient();
    }

    String getUri() {
        return uri.toString();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("uri", uri).toString();
    }
}
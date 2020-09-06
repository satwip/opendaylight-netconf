/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;

/**
 * Web-socket servlet listening on ws or wss schemas for created data-change-event or notification streams.
 */
@Singleton
@SuppressFBWarnings({"SE_NO_SERIALVERSIONID", "SE_BAD_FIELD"})
public class WebSocketInitializer extends WebSocketServlet {

    private final ScheduledExecutorService executorService;
    private final Configuration configuration;
    private final ListenersBroker listenersBroker;

    /**
     * Creation of the web-socket initializer.
     *
     * @param scheduledThreadPool    ODL thread pool used for fetching of scheduled executors.
     * @param configuration          Web-socket configuration holder.
     * @param listenersBroker        Stream listeners register.
     */
    @Inject
    public WebSocketInitializer(final ScheduledThreadPool scheduledThreadPool,
                                final Configuration configuration, final ListenersBroker listenersBroker) {
        this.executorService = scheduledThreadPool.getExecutor();
        this.configuration = configuration;
        this.listenersBroker = listenersBroker;
    }

    /**
     * Configuration of the web-socket factory - idle timeout and specified factory object.
     *
     * @param factory Configurable web-socket factory.
     */
    @Override
    public void configure(final WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(configuration.getIdleTimeout());
        factory.setCreator(new WebSocketFactory(executorService, configuration.getMaximumFragmentLength(),
                configuration.getHeartbeatInterval(), listenersBroker));
    }
}

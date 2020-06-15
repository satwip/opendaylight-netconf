/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TlsAuthChannelInitializer extends ChannelInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(TlsAuthChannelInitializer.class);

    private static final String SSL_HANDLER_CHANNEL_NAME = "sslHandler";

    private final SslHandlerFactory sslHandlerFactory;
    private final GenericFutureListener listener;

    public TlsAuthChannelInitializer(final SslHandlerFactory sslHandlerFactory, GenericFutureListener listener) {
        this.sslHandlerFactory = sslHandlerFactory;
        this.listener = listener;
    }

    @Override
    public void initChannel(Channel ch) {
        SslHandler sslHandler = sslHandlerFactory.createSslHandler();
        sslHandler.handshakeFuture().addListener(listener);
        ch.pipeline().addFirst(SSL_HANDLER_CHANNEL_NAME, sslHandler).fireChannelActive();
    }
}
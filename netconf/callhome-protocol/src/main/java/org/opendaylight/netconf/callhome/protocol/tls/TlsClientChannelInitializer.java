/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* copy-paste from netconf-client */
public class TlsClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {

    public static final String CHANNEL_ACTIVE_SENTRY = "channelActiveSentry";

    private static final Logger LOG = LoggerFactory.getLogger(TlsClientChannelInitializer.class);

    private final SslHandlerFactory sslHandlerFactory;
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    public TlsClientChannelInitializer(final SslHandlerFactory sslHandlerFactory,
                                final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                final NetconfClientSessionListener sessionListener) {
        this.sslHandlerFactory = sslHandlerFactory;
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(Channel ch, Promise<NetconfClientSession> promise) {
        // When ssl handshake fails due to the certificate mismatch, the connection will try again,
        // then we have a chance to create a new SslHandler using the latest certificates with the
        // help of the sentry. We will replace the sentry with the new SslHandler once the channel
        // is active.
        ch.pipeline().addFirst(CHANNEL_ACTIVE_SENTRY, new ChannelActiveSentry(sslHandlerFactory));
        super.initialize(ch, promise);
    }

    @Override
    protected void initializeSessionNegotiator(Channel ch, Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
            negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
    }

    private static final class ChannelActiveSentry extends ChannelInboundHandlerAdapter {
        private final SslHandlerFactory sslHandlerFactory;

        ChannelActiveSentry(final SslHandlerFactory sslHandlerFactory) {
            this.sslHandlerFactory = sslHandlerFactory;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
                ctx.pipeline().replace(this, "sslHandler", sslHandlerFactory.createSslHandler())
                    .fireChannelActive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("An error occurred during communication using Netty channels: {}", cause.getLocalizedMessage());
            ctx.close();
        }
    }
}

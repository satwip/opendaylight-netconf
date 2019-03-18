/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import org.opendaylight.restconf.common.configuration.RestconfConfigurationHolder;

/**
 * {@link WebSocketServerInitializer} is used to setup the {@link ChannelPipeline} of
 * a {@link io.netty.channel.Channel}.
 */
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private SslContext sslContext = null;
    private final RestconfConfigurationHolder.SecurityType securityType;

    WebSocketServerInitializer(final RestconfConfigurationHolder.SecurityType securityType) {
        this.securityType = securityType;
    }

    WebSocketServerInitializer(final RestconfConfigurationHolder.SecurityType securityType,
                               final SslContext sslContext) {
        this(securityType);
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(final SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (sslContext != null) {
            pipeline.addLast(sslContext.newHandler(channel.alloc()));
        }
        pipeline.addLast("codec-http", new HttpServerCodec());
        pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("handler", new WebSocketServerHandler(securityType));
    }
}
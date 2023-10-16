/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.junit.Test;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;

public class SshClientChannelInitializerTest {
    @Test
    public void test() throws Exception {

        AuthenticationHandler authenticationHandler = mock(AuthenticationHandler.class);
        NetconfClientSessionNegotiatorFactory negotiatorFactory = mock(NetconfClientSessionNegotiatorFactory.class);
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);

        NetconfClientSessionNegotiator sessionNegotiator = mock(NetconfClientSessionNegotiator.class);
        doReturn("").when(sessionNegotiator).toString();
        doReturn(sessionNegotiator).when(negotiatorFactory).getSessionNegotiator(any(), any(), any());
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        Channel channel = mock(Channel.class);
        doReturn(pipeline).when(channel).pipeline();
        doReturn("").when(channel).toString();
        doReturn(pipeline).when(pipeline).addFirst(any(ChannelHandler.class));
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));
        ChannelConfig channelConfig = mock(ChannelConfig.class);
        doReturn(channelConfig).when(channel).config();
        doReturn(1L).when(negotiatorFactory).getConnectionTimeoutMillis();
        doReturn(channelConfig).when(channelConfig).setConnectTimeoutMillis(1);

        final var initializer = new SshClientChannelInitializer(authenticationHandler, negotiatorFactory,
            sessionListener);
        initializer.initialize(channel, SettableFuture.create());
        verify(pipeline, times(1)).addFirst(any(ChannelHandler.class));
    }
}

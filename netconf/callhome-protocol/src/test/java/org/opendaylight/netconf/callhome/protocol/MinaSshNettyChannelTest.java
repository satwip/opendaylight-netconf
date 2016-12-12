/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.Buffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;


public class MinaSshNettyChannelTest {
    ClientChannel mockChannel;
    MinaSshNettyChannel instance;

    @Before
    public void setup() {
        IoReadFuture mockFuture = mock(IoReadFuture.class);
        IoInputStream mockIn = mock(IoInputStream.class);
        Mockito.doReturn(mockFuture).when(mockIn).read(any(Buffer.class));
        IoOutputStream mockOut = mock(IoOutputStream.class);

        mockChannel = mock(ClientChannel.class);
        Mockito.doReturn(mockIn).when(mockChannel).getAsyncOut();
        Mockito.doReturn(mockOut).when(mockChannel).getAsyncIn();

        instance = new MinaSshNettyChannel(mockChannel);
    }

    @Test
    @Ignore
    public void OurChannelHandlerShouldBeFirstInThePipeline() {
        // given
        ChannelHandler firstHandler = instance.pipeline().first();
        String firstName = firstHandler.getClass().getName();
        // expect
        assertTrue(firstName.contains("callhome"));
    }

    @Test
    @Ignore
    public void OurChannelHandlerShouldForwardWrites() throws Exception {
        // given
        IoWriteFuture mockFuture = mock(IoWriteFuture.class);
        Mockito.doReturn(mockFuture).when(mockChannel).getAsyncIn().write(any(Buffer.class));

        ChannelHandler mockHandler = mock(ChannelHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Mockito.doReturn(mockHandler).when(ctx).handler();
        ChannelPromise promise = mock(ChannelPromise.class);

        ByteBufAllocator mockAlloc = mock(ByteBufAllocator.class);
        ByteBuf bytes = new EmptyByteBuf(mockAlloc);

        // we would really like to just verify that the async handler write() was
        // called but it is a final class, so no mocking. instead we set up the
        // mock channel to have no async input, which will cause a failure later
        // on the write promise that we use as a cheap way to tell that write()
        // got called. ick.

        Mockito.doReturn(null).when(mockChannel).getAsyncIn();
        instance = new MinaSshNettyChannel(mockChannel);

        // when
        ChannelOutboundHandlerAdapter outadapter = (ChannelOutboundHandlerAdapter) instance.pipeline().first();
        outadapter.write(ctx, bytes, promise);

        // then
        verify(promise, times(1)).setFailure(any(Throwable.class));
    }

}

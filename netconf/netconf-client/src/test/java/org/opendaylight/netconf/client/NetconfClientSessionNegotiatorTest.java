/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.EventLoop;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.NetconfClientSessionPreferences;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.openexi.proc.common.EXIOptions;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetconfClientSessionNegotiatorTest {

    private NetconfHelloMessage helloMessage;
    private ChannelPipeline pipeline;
    private ChannelFuture future;
    private Channel channel;
    private ChannelInboundHandlerAdapter channelInboundHandlerAdapter;

    @Before
    public void setUp() throws Exception {
        helloMessage = NetconfHelloMessage.createClientHello(Sets.newSet("exi:1.0"), Optional.<NetconfHelloMessageAdditionalHeader>absent());
        pipeline = mockChannelPipeline();
        future = mockChannelFuture();
        channel = mockChannel();
        mockEventLoop();
    }

    private static ChannelHandler mockChannelHandler() {
        ChannelHandler handler = mock(ChannelHandler.class);
        return handler;
    }

    private Channel mockChannel() {
        Channel channel = mock(Channel.class);
        ChannelHandler channelHandler = mockChannelHandler();
        doReturn("").when(channel).toString();
        doReturn(future).when(channel).close();
        doReturn(future).when(channel).writeAndFlush(anyObject());
        doReturn(true).when(channel).isOpen();
        doReturn(pipeline).when(channel).pipeline();
        doReturn("").when(pipeline).toString();
        doReturn(pipeline).when(pipeline).remove(any(ChannelHandler.class));
        doReturn(channelHandler).when(pipeline).remove(anyString());
        return channel;
    }

    private static ChannelFuture mockChannelFuture() {
        ChannelFuture future = mock(ChannelFuture.class);
        doReturn(future).when(future).addListener(any(GenericFutureListener.class));
        return future;
    }

    private static ChannelPipeline mockChannelPipeline() {
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        ChannelHandler handler = mock(ChannelHandler.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        doReturn(null).when(pipeline).get(SslHandler.class);
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));
        doReturn(handler).when(pipeline).replace(anyString(), anyString(), any(ChunkedFramingMechanismEncoder.class));

        NetconfXMLToHelloMessageDecoder messageDecoder = new NetconfXMLToHelloMessageDecoder();
        doReturn(messageDecoder).when(pipeline).replace(anyString(), anyString(), any(NetconfXMLToMessageDecoder.class));
        doReturn(pipeline).when(pipeline).replace(any(ChannelHandler.class), anyString(), any(NetconfClientSession.class));
        return pipeline;
    }

    private void mockEventLoop() {
        final EventLoop eventLoop = mock(EventLoop.class);
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                final Object[] args = invocation.getArguments();
                final Runnable runnable = (Runnable) args[0];
                runnable.run();
                return null;
            }
        }).when(eventLoop).execute(any(Runnable.class));
    }

    private NetconfClientSessionNegotiator createNetconfClientSessionNegotiator(final Promise<NetconfClientSession> promise,
                                                                                final NetconfMessage startExi) {
        ChannelProgressivePromise progressivePromise = mock(ChannelProgressivePromise.class);
        NetconfClientSessionPreferences preferences = new NetconfClientSessionPreferences(helloMessage, startExi);
        doReturn(progressivePromise).when(promise).setFailure(any(Throwable.class));

        long timeout = 10L;
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        Timer timer = new HashedWheelTimer();
        return new NetconfClientSessionNegotiator(preferences, promise, channel, timer, sessionListener, timeout);
    }

    private Set<String> CreateCapabilities(String name) throws Exception {
        InputStream stream = NetconfClientSessionNegotiatorTest.class.getResourceAsStream(name);
        Document doc = XmlUtil.readXmlToDocument(stream);
        NetconfHelloMessage hello = new NetconfHelloMessage(doc);

        return ImmutableSet.copyOf(NetconfMessageUtil.extractCapabilitiesFromHello(hello.getDocument()));
    }

    @Test
    public void testNetconfClientSessionNegotiator() throws Exception {
        Promise promise = mock(Promise.class);
        doReturn(promise).when(promise).setSuccess(anyObject());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        negotiator.channelActive(null);
        Set<String> caps = Sets.newSet("a", "b");
        NetconfHelloMessage helloServerMessage = NetconfHelloMessage.createServerHello(caps, 10);
        negotiator.handleMessage(helloServerMessage);
        verify(promise).setSuccess(anyObject());
    }

    @Test
    public void testNetconfClientSessionNegotiatorWithEXI() throws Exception {
        Promise promise = mock(Promise.class);
        EXIOptions exiOptions = new EXIOptions();
        NetconfStartExiMessage exiMessage = NetconfStartExiMessage.create(exiOptions, "msg-id");
        doReturn(promise).when(promise).setSuccess(anyObject());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, exiMessage);

        negotiator.channelActive(null);
        Set<String> caps = Sets.newSet("exi:1.0");
        NetconfHelloMessage helloMessage = NetconfHelloMessage.createServerHello(caps, 10);

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                channelInboundHandlerAdapter = ((ChannelInboundHandlerAdapter) invocationOnMock.getArguments()[2]);
                return null;
            }
        }).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));

        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        doReturn(pipeline).when(handlerContext).pipeline();
        negotiator.handleMessage(helloMessage);
        Document expectedResult = XmlFileLoader.xmlFileToDocument("netconfMessages/rpc-reply_ok.xml");
        channelInboundHandlerAdapter.channelRead(handlerContext, new NetconfMessage(expectedResult));

        verify(promise).setSuccess(anyObject());

        // two calls for exiMessage, 2 for hello message
        verify(pipeline, times(4)).replace(anyString(), anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testNetconfClientSessionNegotiatorGetCached() throws Exception {
        Promise promise = mock(Promise.class);
        doReturn(promise).when(promise).setSuccess(anyObject());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        Set<String> set1 = CreateCapabilities("/helloMessage2.xml");
        Set<String> set2 = CreateCapabilities("/helloMessage1.xml");
        Set<String> set3 = CreateCapabilities("/helloMessage3.xml");

        final Set<String> cachedS1 = negotiator.getCached(set1);
        final Set<String> cachedS2 = negotiator.getCached(set2);
        final Set<String> cachedS3 = negotiator.getCached(set3);

        assert cachedS1 == set1;
        assert cachedS3 == set1;
        assert cachedS3 != set3;
        assert cachedS3.equals(set3);
        assert cachedS2 == set2;
    }

}

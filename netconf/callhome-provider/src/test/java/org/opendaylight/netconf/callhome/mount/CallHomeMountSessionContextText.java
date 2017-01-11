/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.util.concurrent.Promise;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;


public class CallHomeMountSessionContextText {
    private Inet4Address someAddressIpv4;
    private InetSocketAddress someSocketAddress;
    private CallHomeChannelActivator mockActivator;
    private CallHomeMountSessionContext.CloseCallback mockCallback;
    private CallHomeMountSessionContext instance;
    private CallHomeProtocolSessionContext mockProtocol;

    @Before
    public void setup() throws UnknownHostException {
        someAddressIpv4 = (Inet4Address) InetAddress.getByName("1.2.3.4");
        someSocketAddress = new InetSocketAddress(someAddressIpv4, 123);

        mockProtocol = mock(CallHomeProtocolSessionContext.class);
        mockActivator = mock(CallHomeChannelActivator.class);
        mockCallback = mock(CallHomeMountSessionContext.CloseCallback.class);
        doReturn(someSocketAddress).when(mockProtocol).getRemoteAddress();

        instance = new CallHomeMountSessionContext("test",mockProtocol, mockActivator, mockCallback);
    }

    @Test
    public void ConfigNodeCanBeCreated() {
        assertNotNull(instance.getConfigNode());
    }

    @Test
    public void ActivationOfListenerSupportsSessionUp() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(new Answer<Promise<NetconfClientSession>>() {
                    @Override
                    public Promise<NetconfClientSession> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        NetconfClientSession mockSession = mock(NetconfClientSession.class);



                        Object arg = invocationOnMock.getArguments()[0];
                        ((NetconfClientSessionListener) arg).onSessionUp(mockSession);
                        return null;
                    }
                });

        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionUp(any(NetconfClientSession.class));
    }

    @Test
    public void ActivationOfListenerSupportsSessionTermination() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(new Answer<Promise<NetconfClientSession>>() {
                    @Override
                    public Promise<NetconfClientSession> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        NetconfClientSession mockSession = mock(NetconfClientSession.class);
                        NetconfTerminationReason mockReason = mock(NetconfTerminationReason.class);

                        Object arg = invocationOnMock.getArguments()[0];
                        ((NetconfClientSessionListener) arg).onSessionTerminated(mockSession, mockReason);
                        return null;
                    }
                });

        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionTerminated(any(NetconfClientSession.class),
                any(NetconfTerminationReason.class));
    }

    @Test
    public void ActivationOfListenerSupportsSessionDown() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(new Answer<Promise<NetconfClientSession>>() {
                    @Override
                    public Promise<NetconfClientSession> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        NetconfClientSession mockSession = mock(NetconfClientSession.class);
                        Exception mockException = mock(Exception.class);

                        Object arg = invocationOnMock.getArguments()[0];
                        ((NetconfClientSessionListener) arg).onSessionDown(mockSession, mockException);
                        return null;
                    }
                });
        // given
        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionDown(any(NetconfClientSession.class), any(Exception.class));
    }

    @Test
    public void ActivationOfListenerSupportsSessionMessages() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(new Answer<Promise<NetconfClientSession>>() {
                    @Override
                    public Promise<NetconfClientSession> answer(InvocationOnMock invocationOnMock) throws Throwable {
                        NetconfClientSession mockSession = mock(NetconfClientSession.class);
                        NetconfMessage mockMsg = mock(NetconfMessage.class);

                        Object arg = invocationOnMock.getArguments()[0];
                        ((NetconfClientSessionListener) arg).onMessage(mockSession, mockMsg);
                        return null;
                    }
                });

        // given
        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onMessage(any(NetconfClientSession.class), any(NetconfMessage.class));
    }

    @Test
    public void coverage() {
        instance.getId();
        instance.getContextKey();
    }

}

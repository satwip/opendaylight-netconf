/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SessionListener;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoServiceFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCallHomeServerTest {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHomeServerTest.class);

    SshClient mockSshClient;
    CallHomeAuthorizationProvider mockCallHomeAuthProv;
    CallHomeAuthorization mockAuth;
    CallHomeSessionContext.Factory mockFactory;
    InetSocketAddress mockAddress;
    ClientSession mockSession;

    NetconfCallHomeServer instance;

    @Before
    public void setup()
    {
        mockSshClient = Mockito.spy(SshClient.setUpDefaultClient());
        mockCallHomeAuthProv = mock(CallHomeAuthorizationProvider.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockFactory = mock(CallHomeSessionContext.Factory.class);
        mockAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        mockSession = mock(ClientSession.class);

        Map<String,String> props = new HashMap<>();
        props.put("nio-workers", "1");
        Mockito.doReturn(props).when(mockSshClient).getProperties();
        Mockito.doReturn("test").when(mockSession).toString();
        instance = new NetconfCallHomeServer(mockSshClient, mockCallHomeAuthProv, mockFactory, mockAddress);
    }

    @Test
    public void SessionListenerShouldHandleEventsOfKeyEstablishedAndAuthenticated () throws IOException
    {
        // Weird - IJ was ok but command line compile failed using the usual array initializer syntax ????
        SessionListener.Event[] evt = new SessionListener.Event[2];
        evt[0] = SessionListener.Event.KeyEstablished;
        evt[1] = SessionListener.Event.Authenticated;

        int[] hitOpen = new int[2];
        hitOpen[0] = 0;
        hitOpen[1] = 1;

        int[] hitAuth = new int[2];
        hitAuth[0] = 1;
        hitAuth[1] = 0;

        for (int pass = 0; pass < evt.length; pass++)
        {
            // given
            AuthFuture mockAuthFuture = mock(AuthFuture.class);
            Mockito.doReturn(null).when(mockAuthFuture).addListener(any(SshFutureListener.class));
            CallHomeSessionContext mockContext = mock(CallHomeSessionContext.class);
            Mockito.doNothing().when(mockContext).openNetconfChannel();
            Mockito.doReturn(mockContext).when(mockSession).getAttribute(any(Session.AttributeKey.class));
            SessionListener listener = instance.createSessionListener();
            Mockito.doReturn(mockAuthFuture).when(mockContext).authorize();
            // when
            listener.sessionEvent(mockSession, evt[pass]);
            // then
            verify(mockContext, times(hitOpen[pass])).openNetconfChannel();
            verify(mockContext, times(hitAuth[pass])).authorize();
        }
    }

    @Test
    @Ignore
    public void VerificationOfTheServerKeyShouldBeSuccessfulForServerIsAllowed ()
    {
        // given
        ClientSessionImpl mockClientSession = mock(ClientSessionImpl.class);
        Mockito.doReturn("test").when(mockClientSession).toString();
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        Mockito.doReturn("testAddr").when(mockSocketAddr).toString();
        PublicKey mockPublicKey = mock(PublicKey.class);

        CallHomeAuthorization mockAuth = mock(CallHomeAuthorization.class);
        Mockito.doReturn("test").when(mockAuth).toString();
        Mockito.doReturn(true).when(mockAuth).isServerAllowed();

        Mockito.doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr,mockPublicKey);

        // expect
        instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey);
    }

    @Test
    public void VerificationOfTheServerKeyShouldFailIfTheServerIsNotAllowed ()
    {
        // given

        ClientSessionImpl mockClientSession = mock(ClientSessionImpl.class);
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        PublicKey mockPublicKey = mock(PublicKey.class);

        Mockito.doReturn(false).when(mockAuth).isServerAllowed();
        Mockito.doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);

        // expect
        assertFalse(instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey));
    }

    static class TestableCallHomeServer extends NetconfCallHomeServer
    {
        static IoServiceFactory minaServiceFactory;
        static SshClient factoryHook (SshClient client, IoServiceFactory minaFactory)
        {
            minaServiceFactory = minaFactory;
            return client;
        }

        SshClient client;

        TestableCallHomeServer(SshClient sshClient, CallHomeAuthorizationProvider authProvider,
                                   CallHomeSessionContext.Factory factory, InetSocketAddress socketAddress,
                                   IoServiceFactory minaFactory)
        {
            super(factoryHook(sshClient, minaFactory), authProvider, factory, socketAddress);
            client = sshClient;
        }

        @Override
        protected IoServiceFactory createMinaServiceFactory(SshClient sshClient)
        {
            return minaServiceFactory;
        }
    }

    @Test
    public void BindShouldStartTheClientAndBindTheAddress () throws IOException
    {
        // given
        IoAcceptor mockAcceptor = mock(IoAcceptor.class);
        IoServiceFactory mockMinaFactory = mock(IoServiceFactory.class);

        Mockito.doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        Mockito.doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        Mockito.doNothing().when(mockAcceptor).bind(mockAddress);
        instance = new TestableCallHomeServer(mockSshClient, mockCallHomeAuthProv, mockFactory, mockAddress, mockMinaFactory);
        // when
        instance.bind();
        // then
        verify(mockSshClient, times(1)).start();
        verify(mockAcceptor, times(1)).bind(mockAddress);
    }

    @Ignore
    @Test
    public void test() throws InterruptedException {

        CallHomeAuthorizationProvider auth = new CallHomeAuthorizationProvider() {

            @Override
            public CallHomeAuthorization provideAuth(SocketAddress remoteAddress, PublicKey serverKey) {
                return CallHomeAuthorization.serverAccepted("vyatta").addPassword("vyatta").build();
            }
        };

        NetconfClientSessionListener sessionListener = new NetconfClientSessionListener() {

            @Override
            public void onSessionUp(NetconfClientSession session) {
                LOG.info("NETCONF Session UP {}", session.getServerCapabilities());
                // Sends a message on particular session
                //session.sendMessage(netconfMessage);
            }

            @Override
            public void onSessionTerminated(NetconfClientSession session, NetconfTerminationReason reason) {
                LOG.info("Session terminated.");

            }

            @Override
            public void onSessionDown(NetconfClientSession session, Exception e) {
                LOG.info("Session down.");

            }

            @Override
            public void onMessage(NetconfClientSession session, NetconfMessage message) {
                LOG.info("NETCONF Session Message {}", session, message);
            }
        };

        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(auth, sessionListener);
        NetconfCallHomeServer server = builder.build();
        server.bind();

        Thread.sleep(TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    }

}

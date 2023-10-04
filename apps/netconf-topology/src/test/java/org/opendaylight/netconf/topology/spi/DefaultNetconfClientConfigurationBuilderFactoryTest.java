/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPassword;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class DefaultNetconfClientConfigurationBuilderFactoryTest {
    private static final NodeId NODE_ID = new NodeId("testing-node");
    private static final Host HOST = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
    private static final PortNumber PORT = new PortNumber(Uint16.valueOf(9999));
    private static final String USERNAME = "test-username";
    private static final String PASSWORD = "test-password";

    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;
    @Mock
    private SslHandlerFactoryProvider sslHandlerFactoryProvider;
    @Mock
    private SslHandlerFactory sslHandlerFactory;

    private NetconfNodeBuilder nodeBuilder;
    private DefaultNetconfClientConfigurationBuilderFactory factory;

    @BeforeEach
    void beforeEach() {
        nodeBuilder = new NetconfNodeBuilder()
            .setHost(HOST)
            .setPort(PORT)
            .setReconnectOnChangedSchema(true)
            .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
            .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(100))
            .setKeepaliveDelay(Uint32.valueOf(1000))
            .setCredentials(new LoginPasswordBuilder().setUsername(USERNAME).setPassword(PASSWORD).build())
            .setMaxConnectionAttempts(Uint32.ZERO)
            .setSleepFactor(Decimal64.valueOf("1.5"))
            .setConnectionTimeoutMillis(Uint32.valueOf(20000));
        factory = new DefaultNetconfClientConfigurationBuilderFactory(encryptionService, credentialProvider,
            sslHandlerFactoryProvider);
    }

    @Test
    void testDefault() {
        final var config = createConfig(nodeBuilder.setTcpOnly(false).build());
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertTcpParameters(config.getTcpParameters());
        assertSshParameters(config.getSshParameters());
        assertNull(config.getSslHandlerFactory());
    }

    @Test
    void testSsh() {
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.SSH).build()).build());
        assertEquals(NetconfClientProtocol.SSH, config.getProtocol());
        assertTcpParameters(config.getTcpParameters());
        assertSshParameters(config.getSshParameters());
    }

    @Test
    public void testTcp() {
        final var config = createConfig(nodeBuilder.setTcpOnly(true).build());
        assertEquals(NetconfClientProtocol.TCP, config.getProtocol());
        assertTcpParameters(config.getTcpParameters());
    }

    @Test
    public void testTls() {
        doReturn(sslHandlerFactory).when(sslHandlerFactoryProvider).getSslHandlerFactory(null);
        final var config = createConfig(
            nodeBuilder.setTcpOnly(false).setProtocol(new ProtocolBuilder().setName(Name.TLS).build()).build());
        assertEquals(NetconfClientProtocol.TLS, config.getProtocol());
        assertTcpParameters(config.getTcpParameters());
        assertNotNull(config.getTransportSslHandlerFactory());
    }

    private NetconfClientConfiguration createConfig(final NetconfNode netconfNode) {
        return factory.createClientConfigurationBuilder(NODE_ID, netconfNode)
            .withSessionListener(sessionListener)
            .build();
    }

    private static void assertTcpParameters(TcpClientGrouping tcpParameters) {
        assertNotNull(tcpParameters);
        assertEquals(HOST, tcpParameters.getRemoteAddress());
        assertEquals(PORT, tcpParameters.getRemotePort());
    }

    private static void assertSshParameters(SshClientGrouping sshParameters) {
        assertNotNull(sshParameters);
        assertNotNull(sshParameters.getClientIdentity());
        assertEquals(USERNAME, sshParameters.getClientIdentity().getUsername());
        assertNotNull(sshParameters.getClientIdentity().getPassword());
        final var passType = assertInstanceOf(CleartextPassword.class,
            sshParameters.getClientIdentity().getPassword().getPasswordType());
        assertEquals(PASSWORD, passType.getCleartextPassword());
    }
}

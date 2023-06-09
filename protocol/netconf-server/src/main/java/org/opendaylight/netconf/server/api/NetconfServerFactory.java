/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Consumer;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.TlsServerGrouping;

/**
 * Basic interface for Netconf server factory.
 */
public interface NetconfServerFactory {

    /**
     * Build Netconf server operating over TCP transport.
     *
     * @param params - TCP transport configuration
     * @return server instance as future
     * @throws UnsupportedConfigurationException if server cannot be started using given configuration
     * @throws IllegalArgumentException if params is null
     */
    ListenableFuture<TCPServer> createTcpServer(TcpServerGrouping params) throws UnsupportedConfigurationException;

    /**
     * Build Netconf server operating over TCP transport with TLS overlay.
     *
     * @param tcpParams - TCP transport configuration
     * @param tlsParams - TLS overlay configuration
     * @return server instance as future
     * @throws UnsupportedConfigurationException if server cannot be started using given configuration
     * @throws IllegalArgumentException if either tcpParams or tlsParams is null
     */
    ListenableFuture<TLSServer> createTlsServer(TcpServerGrouping tcpParams, TlsServerGrouping tlsParams)
        throws UnsupportedConfigurationException;

    /**
     * Build SSH Netconf server.
     *
     * @param tcpParams TCP transport configuration
     * @param sshParams SSH overlay configuration
     * @return server instance as future
     * @throws UnsupportedConfigurationException if server cannot be started using given configuration
     * @throws IllegalArgumentException if either tcpParams or sshParams is null
     */
    ListenableFuture<SSHServer> createSshServer(TcpServerGrouping tcpParams, SshServerGrouping sshParams)
        throws UnsupportedConfigurationException;

    /**
     * Build SSH Netconf server with integration support.
     *
     * @param tcpParams TCP transport configuration
     * @param sshParams SSH overlay configuration
     * @param extFactoryConfiguration explicit server factory configuration (if defined sshParams became optional)
     * @return server instance as future
     * @throws UnsupportedConfigurationException if server cannot be started using given configuration
     * @throws IllegalArgumentException if either tcpParams or sshParams is null or extFactoryConfiguration is null
     */
    ListenableFuture<SSHServer> createSshServer(TcpServerGrouping tcpParams, SshServerGrouping sshParams,
        Consumer<ServerFactoryManager> extFactoryConfiguration) throws UnsupportedConfigurationException;

}

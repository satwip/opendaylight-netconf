/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.stress;

import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.protocol.framework.NeverReconnectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StressClientCallable implements Callable<Boolean>{

    private static final Logger LOG = LoggerFactory.getLogger(StressClientCallable.class);

    private Parameters params;
    private final NetconfDeviceCommunicator sessionListener;
    private final NetconfClientDispatcherImpl netconfClientDispatcher;
    private final NetconfClientConfiguration cfg;
    private final NetconfClientSession netconfClientSession;
    private final ExecutionStrategy executionStrategy;

    public StressClientCallable(final Parameters params,
                                final NetconfClientDispatcherImpl netconfClientDispatcher,
                                final List<NetconfMessage> preparedMessages) {
        this.params = params;
        this.sessionListener = getSessionListener(params.getInetAddress());
        this.netconfClientDispatcher = netconfClientDispatcher;
        cfg = getNetconfClientConfiguration(this.params, this.sessionListener);

        LOG.info("Connecting to netconf server {}:{}", params.ip, params.port);
        try {
            netconfClientSession = netconfClientDispatcher.createClient(cfg).get();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        } catch (final ExecutionException e) {
            throw new RuntimeException("Unable to connect", e);
        }
        executionStrategy = getExecutionStrategy(params, preparedMessages, sessionListener);
    }

    @Override
    public Boolean call() throws Exception {
        executionStrategy.invoke();
        netconfClientSession.close();
        return true;
    }

    private static ExecutionStrategy getExecutionStrategy(final Parameters params, final List<NetconfMessage> preparedMessages, final NetconfDeviceCommunicator sessionListener) {
        if(params.async) {
            return new AsyncExecutionStrategy(params, preparedMessages, sessionListener);
        } else {
            return new SyncExecutionStrategy(params, preparedMessages, sessionListener);
        }
    }

    private static NetconfDeviceCommunicator getSessionListener(final InetSocketAddress inetAddress) {
        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> loggingRemoteDevice = new StressClient.LoggingRemoteDevice();
        return new NetconfDeviceCommunicator(new RemoteDeviceId("secure-test", inetAddress), loggingRemoteDevice);
    }

    private static NetconfClientConfiguration getNetconfClientConfiguration(final Parameters params, final NetconfDeviceCommunicator sessionListener) {
        final NetconfClientConfigurationBuilder netconfClientConfigurationBuilder = NetconfClientConfigurationBuilder.create();
        netconfClientConfigurationBuilder.withSessionListener(sessionListener);
        netconfClientConfigurationBuilder.withAddress(params.getInetAddress());
        if(params.tcpHeader != null) {
            final String header = params.tcpHeader.replaceAll("\"", "").trim() + "\n";
            netconfClientConfigurationBuilder.withAdditionalHeader(new NetconfHelloMessageAdditionalHeader(null, null, null, null, null) {
                @Override
                public String toFormattedString() {
                    LOG.debug("Sending TCP header {}", header);
                    return header;
                }
            });
        }
        netconfClientConfigurationBuilder.withProtocol(params.ssh ? NetconfClientConfiguration.NetconfClientProtocol.SSH : NetconfClientConfiguration.NetconfClientProtocol.TCP);
        netconfClientConfigurationBuilder.withAuthHandler(new LoginPassword(params.username, params.password));
        netconfClientConfigurationBuilder.withConnectionTimeoutMillis(20000L);
        netconfClientConfigurationBuilder.withReconnectStrategy(new NeverReconnectStrategy(GlobalEventExecutor.INSTANCE, 5000));
        return netconfClientConfigurationBuilder.build();
    }
}

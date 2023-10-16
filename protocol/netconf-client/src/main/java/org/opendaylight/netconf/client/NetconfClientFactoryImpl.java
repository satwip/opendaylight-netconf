/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.SSH;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TCP;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TLS;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import javax.inject.Singleton;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(immediate = true, service = NetconfClientFactory.class, property = "type=netconf-client-factory")
public class NetconfClientFactoryImpl implements NetconfClientFactory {
    private final Timer timer = new HashedWheelTimer();
    private final SSHTransportStackFactory factory;

    @Activate
    public NetconfClientFactoryImpl() {
        // TODO make factory component configurable for osgi
        this(new SSHTransportStackFactory("odl-netconf-client", 0));
    }

    public NetconfClientFactoryImpl(final SSHTransportStackFactory factory) {
        this.factory = requireNonNull(factory);
    }

    @Deactivate
    @Override
    public void close() {
        timer.stop();
        factory.close();
    }

    @Override
    public ListenableFuture<NetconfClientSession> createClient(final NetconfClientConfiguration configuration)
            throws UnsupportedConfigurationException {
        final var protocol = configuration.getProtocol();
        final var future = SettableFuture.<NetconfClientSession>create();
        final var channelInitializer = new ClientChannelInitializer(createNegotiatorFactory(configuration),
            () -> configuration.getSessionListener());
        final var bootstrap = factory.newBootstrap();

        if (TCP.equals(protocol)) {
            TCPClient.connect(new ClientTransportChannelListener(future, channelInitializer), bootstrap,
                configuration.getTcpParameters());
        } else if (TLS.equals(protocol)) {
            if (configuration.getTlsParameters() != null) {
                TLSClient.connect(new ClientTransportChannelListener(future, channelInitializer), bootstrap,
                    configuration.getTcpParameters(), configuration.getTlsParameters());
            } else {
                TLSClient.connect(new ClientTransportChannelListener(future, channelInitializer), bootstrap,
                    configuration.getTcpParameters(), configuration.getTransportSslHandlerFactory());
            }
        } else if (SSH.equals(protocol)) {
            factory.connectClient("netconf", new ClientTransportChannelListener(future, channelInitializer),
                configuration.getTcpParameters(), configuration.getSshParameters());
        }
        return future;
    }

    private NetconfClientSessionNegotiatorFactory createNegotiatorFactory(
            final NetconfClientConfiguration configuration) {
        final var capabilities = configuration.getOdlHelloCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
                configuration.getConnectionTimeoutMillis(), configuration.getMaximumIncomingChunkSize());
        }
        final var stringCapabilities = capabilities.stream().map(Uri::getValue)
            .collect(ImmutableSet.toImmutableSet());
        return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
            configuration.getConnectionTimeoutMillis(), stringCapabilities);
    }

    private record ClientTransportChannelListener(
            SettableFuture<NetconfClientSession> future,
            ClientChannelInitializer initializer) implements TransportChannelListener {
        ClientTransportChannelListener {
            requireNonNull(future);
            requireNonNull(initializer);
        }

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            initializer.initialize(channel.channel(), future);
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            future.setException(cause);
        }
    }
}

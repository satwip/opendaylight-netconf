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
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.ClientSubsystemFactory;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(immediate = true, service = NetconfClientFactory.class, property = "type=netconf-client-factory")
public class NetconfClientFactoryImpl implements NetconfClientFactory {
    private final EventLoopGroup group;
    private final Timer timer;
    private final SSHTransportStackFactory sshTransportStackFactory;

    @Inject
    @Activate
    public NetconfClientFactoryImpl(@Reference final Timer timer) {
        this.timer = requireNonNull(timer);
        // TODO pass group as transport compatible OSGi component
        this.group = NettyTransportSupport.newEventLoopGroup("netconf-client-group");
        // TODO make TransportFactoryHolder (transport-nb) a shared component then use here as factory source
        this.sshTransportStackFactory = new SSHTransportStackFactory("odl-netconf-client-grp", 0);
    }

    @Deactivate
    @Override
    public void close() {
        timer.stop();
        group.shutdownGracefully();
        sshTransportStackFactory.close();
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration configuration)
            throws UnsupportedConfigurationException {
        final var protocol = configuration.getProtocol();
        final var promise = new DefaultPromise<NetconfClientSession>(GlobalEventExecutor.INSTANCE);
        final var channelInitializer = new ClientChannelInitializer(createNegotiatorFactory(configuration),
            () -> configuration.getSessionListener());
        final var bootstrap = NettyTransportSupport.newBootstrap().group(group);

        if (TCP.equals(protocol)) {
            TCPClient.connect(createTransportChannelListener(promise, channelInitializer), bootstrap,
                configuration.getTcpParameters());
        } else if (TLS.equals(protocol)) {
            TLSClient.connect(createTransportChannelListener(promise, channelInitializer), bootstrap,
                configuration.getTcpParameters(), configuration.getTlsParameters());
        } else if (SSH.equals(protocol)) {
            sshTransportStackFactory.connectClient(createTransportChannelListener(promise, null),
                configuration.getTcpParameters(), configuration.getSshParameters(),
                createNetconfSubsystemFactory(promise, channelInitializer));
        }
        return promise;
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

    private static TransportChannelListener createTransportChannelListener(
        final @NonNull Promise<NetconfClientSession> promise,
        final @Nullable ClientChannelInitializer channelInitializer) {

        return new TransportChannelListener() {
            @Override
            public void onTransportChannelEstablished(@NonNull TransportChannel channel) {
                if (channelInitializer != null) {
                    channelInitializer.initialize(channel.channel(), promise);
                }
            }

            @Override
            public void onTransportChannelFailed(@NonNull Throwable cause) {
                promise.setFailure(cause);
            }
        };
    }

    private static ClientSubsystemFactory createNetconfSubsystemFactory(
            final Promise<NetconfClientSession> promise, final ClientChannelInitializer channelInitializer) {
        return new ClientSubsystemFactory() {
            @Override
            public @NonNull String subsystemName() {
                return "netconf";
            }

            @Override
            public @NonNull ChannelSubsystem createSubsystemChannel() {
                return new NetconfChannelSubsystem(channelInitializer, promise);
            }
        };
    }
}

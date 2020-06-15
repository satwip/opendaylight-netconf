/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.callhome.protocol.TransportType;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CallHomeTlsSessionContext implements CallHomeProtocolSessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeTlsSessionContext.class);

    private final SslHandlerFactory sslHandlerFactory;
    private final CallHomeNetconfSubsystemListener subsystemListener;

    private volatile boolean activated;
    private final TransportType transportType;
    private final String channelId;
    private final Channel channel;
    private SocketAddress socketAddress;

    public CallHomeTlsSessionContext(final Channel channel, final SslHandlerFactory sslHandlerFactory,
                                     final CallHomeNetconfSubsystemListener subsystemListener) {
        this.channel = requireNonNull(channel, "channel");
        this.channelId = channel.id().asLongText();
        this.socketAddress = channel.remoteAddress();
        this.sslHandlerFactory = requireNonNull(sslHandlerFactory, "sslHandlerFactory");
        this.subsystemListener = subsystemListener;
        this.transportType = TransportType.TLS;
    }

    void openNetconfChannel(Channel ch) {
        LOG.debug("Opening NETCONF Subsystem on TLS connection {}", channelId);
        subsystemListener.onNetconfSubsystemOpened(this,
            listener -> doActivate(ch, listener));
    }

    @Override
    public void terminate() {
        channel.close();
    }

    private synchronized Promise<NetconfClientSession> doActivate(final Channel channel,
            final NetconfClientSessionListener listener) {
        if (activated) {
            return newSessionPromise().setFailure(new IllegalStateException("Session (channel) already activated."));
        }

        activated = true;
        LOG.info("Activating Netconf channel for {} with {}", getRemoteAddress(), listener);
        Promise<NetconfClientSession> activationPromise = newSessionPromise();
        NetconfClientSessionNegotiatorFactory negotiatorFactory = new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
            Optional.empty(), TimeUnit.SECONDS.toMillis(5));
        TlsClientChannelInitializer tlsClientChannelInitializer = new TlsClientChannelInitializer(sslHandlerFactory,
            negotiatorFactory, listener);
        tlsClientChannelInitializer.initialize(channel, activationPromise);
        return activationPromise;
    }

    private static Promise<NetconfClientSession> newSessionPromise() {
        return GlobalEventExecutor.INSTANCE.newPromise();
    }

    @Override
    public PublicKey getRemoteServerKey() {
        // FIXME: Create a separate Interface(i.e. SshAwareContext) and don't implement this
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.socketAddress;
    }

    @Override
    public String getSessionId() {
        return channelId;
    }

    public TransportType getTransportType() {
        return TransportType.TLS;
    }
}

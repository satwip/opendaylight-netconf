/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfClientBuilder;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.cipher.BuiltinCiphers;
import org.opendaylight.netconf.shaded.sshd.common.cipher.Cipher;
import org.opendaylight.netconf.shaded.sshd.common.cipher.CipherFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.AbstractTransportStackBootstrap;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.AeadAes128Gcm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.AeadAes256Gcm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes128Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes128Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes192Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes192Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes256Cbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Aes256Ctr;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Arcfour128;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.Arcfour256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.BlowfishCbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.EncryptionAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.None;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.encryption.algs.rev220616.TripleDesCbc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup15Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup16Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup17Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup18Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup1Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.KeyExchangeAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd5;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd596;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha196;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.MacAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.TransportParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.KeyExchange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Mac;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev220718.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.concepts.Mutable;

/**
 * A bootstrap allowing instantiation of {@link SSHTransportStack}s.
 */
public abstract sealed class SSHTransportStackBootstrap extends AbstractTransportStackBootstrap {
    private static final class Client extends SSHTransportStackBootstrap {
        private final SshClientGrouping parameters;

        private Client(final TransportChannelListener listener, final SshClientGrouping parameters) {
            super(listener, CLIENT_KEXS);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        protected int tcpConnectPort() {
            return NETCONF_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return CALLHOME_PORT;
        }

        @Override
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            final var builder = newClientBuilder();


            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        private @NonNull NetconfClientBuilder newClientBuilder() throws UnsupportedConfigurationException {
            final var builder = new NetconfClientBuilder();

            final var clientIdentity = parameters.getClientIdentity();
            if (clientIdentity != null) {
                // FIXME: implement this
            }
            final var serverAuthentication = parameters.getServerAuthentication();
            if (serverAuthentication != null) {
                // FIXME: implement this
            }
            final var keepAlives = parameters.getKeepalives();
            if (keepAlives != null) {
                // FIXME: implement this
            }

            setTransportParams(builder, parameters.getTransportParams());

            return builder;
        }
    }

    private static final class Server extends SSHTransportStackBootstrap {
        private final SshServerGrouping parameters;

        private Server(final TransportChannelListener listener, final SshServerGrouping parameters) {
            super(listener, SERVER_KEXS);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        protected int tcpConnectPort() {
            return CALLHOME_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return NETCONF_PORT;
        }

        @Override
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    public static final class ClientBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshClientGrouping parameters;

        private ClientBuilder() {
            // Hidden on purpose
        }

        public @NonNull ClientBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ClientBuilder setParameters(final SshClientGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Client(listener, parameters);
        }
    }

    public static final class ServerBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshServerGrouping parameters;

        private ServerBuilder() {
            // Hidden on purpose
        }

        public @NonNull ServerBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ServerBuilder setParameters(final SshServerGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Server(listener, parameters);
        }
    }

    private static final int NETCONF_PORT = 830;
    private static final int CALLHOME_PORT = 4334;

    private static final ImmutableMap<EncryptionAlgBase, CipherFactory> CIPHERS =
        ImmutableMap.<EncryptionAlgBase, CipherFactory>builder()
            .put(AeadAes128Gcm.VALUE, BuiltinCiphers.aes128gcm)
            .put(AeadAes256Gcm.VALUE, BuiltinCiphers.aes256cbc)
            .put(Aes128Cbc.VALUE, BuiltinCiphers.aes128cbc)
            .put(Aes128Ctr.VALUE, BuiltinCiphers.aes128ctr)
            .put(Aes192Cbc.VALUE, BuiltinCiphers.aes192cbc)
            .put(Aes192Ctr.VALUE, BuiltinCiphers.aes192ctr)
            .put(Aes256Cbc.VALUE, BuiltinCiphers.aes256cbc)
            .put(Aes256Ctr.VALUE, BuiltinCiphers.aes256ctr)
            .put(Arcfour128.VALUE, BuiltinCiphers.arcfour128)
            .put(Arcfour256.VALUE, BuiltinCiphers.arcfour256)
            .put(BlowfishCbc.VALUE, BuiltinCiphers.blowfishcbc)
            .put(TripleDesCbc.VALUE, BuiltinCiphers.tripledescbc)
            .put(None.VALUE, BuiltinCiphers.none)
            .build();

    private static final ImmutableMap<KeyExchangeAlgBase, KeyExchangeFactory> CLIENT_KEXS;
    private static final ImmutableMap<KeyExchangeAlgBase, KeyExchangeFactory> SERVER_KEXS;

    static {
        final var factories = Maps.filterValues(ImmutableMap.<KeyExchangeAlgBase, BuiltinDHFactories>builder()
            .put(Curve25519Sha256.VALUE, BuiltinDHFactories.curve25519)
            .put(Curve448Sha512.VALUE, BuiltinDHFactories.curve448)
            .put(DiffieHellmanGroup1Sha1.VALUE, BuiltinDHFactories.dhg1)
            .put(DiffieHellmanGroup14Sha1.VALUE, BuiltinDHFactories.dhg14)
            .put(DiffieHellmanGroup14Sha256.VALUE, BuiltinDHFactories.dhg14_256)
            .put(DiffieHellmanGroup15Sha512.VALUE, BuiltinDHFactories.dhg15_512)
            .put(DiffieHellmanGroup16Sha512.VALUE, BuiltinDHFactories.dhg16_512)
            .put(DiffieHellmanGroup17Sha512.VALUE, BuiltinDHFactories.dhg17_512)
            .put(DiffieHellmanGroup18Sha512.VALUE, BuiltinDHFactories.dhg18_512)
            .put(DiffieHellmanGroupExchangeSha1.VALUE, BuiltinDHFactories.dhgex)
            .put(DiffieHellmanGroupExchangeSha256.VALUE, BuiltinDHFactories.dhgex256)
//            .put(EcdhSha21284010045311.VALUE, null)
//            .put(EcdhSha213132016.VALUE, null)
//            .put(EcdhSha21313201.VALUE, null)
//            .put(EcdhSha213132026.VALUE, null)
//            .put(EcdhSha213132027.VALUE, null)
//            .put(EcdhSha213132033.VALUE, null)
//            .put(EcdhSha213132036.VALUE, null)
//            .put(EcdhSha213132037.VALUE, null)
//            .put(EcdhSha213132038.VALUE, null)
            .put(EcdhSha2Nistp256.VALUE, BuiltinDHFactories.ecdhp256)
            .put(EcdhSha2Nistp384.VALUE, BuiltinDHFactories.ecdhp384)
            .put(EcdhSha2Nistp521.VALUE, BuiltinDHFactories.ecdhp521)
//            .put(EcmqvSha2.VALUE, null)
//            .put(ExtInfoC.VALUE, null)
//            .put(ExtInfoS.VALUE, null)
            // FIXME: Gss* ?
            .build(), BuiltinDHFactories::isSupported);

        CLIENT_KEXS = ImmutableMap.copyOf(
            Maps.transformValues(factories, org.opendaylight.netconf.shaded.sshd.client.ClientBuilder.DH2KEX::apply));
        SERVER_KEXS = ImmutableMap.copyOf(
            Maps.transformValues(factories, org.opendaylight.netconf.shaded.sshd.server.ServerBuilder.DH2KEX::apply));
    }

    private static final ImmutableMap<MacAlgBase, MacFactory> MACS = ImmutableMap.<MacAlgBase, MacFactory>builder()
        .put(HmacMd5.VALUE, BuiltinMacs.hmacmd5)
        .put(HmacMd596.VALUE, BuiltinMacs.hmacmd596)
        .put(HmacSha1.VALUE, BuiltinMacs.hmacsha1)
        .put(HmacSha196.VALUE, BuiltinMacs.hmacsha196)
        .put(HmacSha2256.VALUE, BuiltinMacs.hmacsha256)
        .put(HmacSha2512.VALUE, BuiltinMacs.hmacsha512)
        // FIXME: resolve by name?!
        // FIXME: AeadAes128Gcm.VALUE
        // FIXME: AeadAes256Gcm.VALUE
        // FIXME: None.VALUE
        // FIXME openssh ETM extensions
        .build();

    private final ImmutableMap<KeyExchangeAlgBase, KeyExchangeFactory> kexs;

    SSHTransportStackBootstrap(final TransportChannelListener listener,
            final ImmutableMap<KeyExchangeAlgBase, KeyExchangeFactory> kexs) {
        super(listener);
        this.kexs = requireNonNull(kexs);
    }

    public static @NonNull ClientBuilder clientBuilder() {
        return new ClientBuilder();
    }

    @Override
    public final CompletionStage<TransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass
        final var remoteHost = require(connectParams, TcpClientGrouping::getRemoteAddress, "remote-address");
        final var remotePort = require(connectParams, TcpClientGrouping::getRemotePort, "remote-port");
        final var remoteAddr = remoteHost.getIpAddress();

        return initiate(addressOf(connectParams.getLocalAddress(), connectParams.getLocalPort(), 0),
            remoteAddr != null ? addressOf(remoteAddr, remotePort, tcpConnectPort())
                : new SshdSocketAddress(
                    require(remoteHost, Host::getDomainName, "remote-address/domain-name").getValue(),
                    portNumber(remotePort, tcpConnectPort())));
    }

    abstract @NonNull CompletionStage<TransportStack> initiate(SshdSocketAddress local, SshdSocketAddress remote)
        throws UnsupportedConfigurationException;

    @Override
    public final CompletionStage<TransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass

        return listen(addressOf(require(listenParams, TcpServerGrouping::getLocalAddress, "local-address"),
            listenParams.getLocalPort(), tcpListenPort()));
    }

    abstract @NonNull CompletionStage<TransportStack> listen(SshdSocketAddress local)
        throws UnsupportedConfigurationException;

    private static SshdSocketAddress addressOf(final @Nullable IpAddress address,
            final @Nullable PortNumber port, final int defaultPort) {
        final int portNum = portNumber(port, defaultPort);
        return address == null ?  new SshdSocketAddress(portNum)
            : new SshdSocketAddress(new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(address), portNum));
    }

    final void setTransportParams(final @NonNull BaseBuilder<?, ?> builder,
            final @Nullable TransportParamsGrouping params) throws UnsupportedConfigurationException {
        if (params != null) {
            setEncryption(builder, params.getEncryption());
            setHostKey(builder, params.getHostKey());
            setKeyExchange(builder, params.getKeyExchange());
            setMac(builder, params.getMac());
        }
    }

    private static void setEncryption(final BaseBuilder<?, ?> baseBuilder, final Encryption encryption)
            throws UnsupportedConfigurationException {
        if (encryption != null) {
            final var encAlg = encryption.getEncryptionAlg();
            if (encAlg != null && !encAlg.isEmpty()) {
                baseBuilder.cipherFactories(createCipherFactories(encAlg));
            }
        }
    }

    private static List<NamedFactory<Cipher>> createCipherFactories(final List<EncryptionAlgBase> encAlg)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<NamedFactory<Cipher>>builderWithExpectedSize(encAlg.size());
        for (var alg : encAlg) {
            builder.add(cipherFactoryOf(alg));
        }
        return builder.build();
    }

    private static @NonNull CipherFactory cipherFactoryOf(final EncryptionAlgBase alg)
            throws UnsupportedConfigurationException {
        final var ret = CIPHERS.get(alg);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Unsupported MAC algorithm " + alg);
        }
        return ret;
    }

    private static void setHostKey(final BaseBuilder<?, ?> baseBuilder, final HostKey hostKey)
            throws UnsupportedConfigurationException {
        if (hostKey != null) {
            final var hostKeyAlg = hostKey.getHostKeyAlg();
            if (hostKeyAlg != null && hostKeyAlg.isEmpty()) {
                // FIXME: implement this
            }
        }
    }

    private void setKeyExchange(final BaseBuilder<?, ?> baseBuilder, final KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                baseBuilder.keyExchangeFactories(createKexFactories(kexAlg));
            }
        }
    }

    private List<KeyExchangeFactory> createKexFactories(final List<KeyExchangeAlgBase> kexAlg)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<KeyExchangeFactory>builderWithExpectedSize(kexAlg.size());
        for (var alg : kexAlg) {
            builder.add(kexFactoryOf(alg));
        }
        return builder.build();
    }

    private @NonNull KeyExchangeFactory kexFactoryOf(final KeyExchangeAlgBase alg)
            throws UnsupportedConfigurationException {
        final var ret = kexs.get(alg);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Unsupported key exchange algorithm " + alg);
        }
        return ret;
    }

    private static void setMac(final BaseBuilder<?, ?> baseBuilder, final Mac mac)
            throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null && !macAlg.isEmpty()) {
                baseBuilder.macFactories(createMacFactories(macAlg));
            }
        }
    }

    private static List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> createMacFactories(
            final List<MacAlgBase> macAlg) throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>>
            builderWithExpectedSize(macAlg.size());
        for (var alg : macAlg) {
            builder.add(macFactoryOf(alg));
        }
        return builder.build();
    }

    private static @NonNull MacFactory macFactoryOf(final MacAlgBase alg) throws UnsupportedConfigurationException {
        final var ret = MACS.get(alg);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Unsupported MAC algorithm " + alg);
        }
        return ret;
    }
}

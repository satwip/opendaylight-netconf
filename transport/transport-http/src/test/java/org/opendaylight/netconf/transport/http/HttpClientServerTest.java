/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.http.ConfigUtils.clientTransportTcp;
import static org.opendaylight.netconf.transport.http.ConfigUtils.clientTransportTls;
import static org.opendaylight.netconf.transport.http.ConfigUtils.serverTransportTcp;
import static org.opendaylight.netconf.transport.http.ConfigUtils.serverTransportTls;

import com.google.common.util.concurrent.Futures;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;

@ExtendWith(MockitoExtension.class)
public class HttpClientServerTest {
//    private static final Logger LOG = LoggerFactory.getLogger(HttpClientServerTest.class);
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";
    private static final Map<String, String> USER_HASHES_MAP = Map.of(USERNAME, "$0$" + PASSWORD);
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String[] METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};
    private static final String RESPONSE_TEMPLATE = "Method: %s URI: %s Payload: %s";

    private static final RequestDispatcher DISPATCHER = request -> {
        // return 200 response with a content built from request parameters
        final var method = request.method().name();
        final var uri = request.uri();
        final var payload = request.content().readableBytes() > 0
            ? request.content().toString(StandardCharsets.UTF_8) : "";
        final var responseMessage = RESPONSE_TEMPLATE.formatted(method, uri, payload);
        final var response = new DefaultFullHttpResponse(request.protocolVersion(), OK,
            wrappedBuffer(responseMessage.getBytes(StandardCharsets.UTF_8)));
        response.headers().set(CONTENT_TYPE, TEXT_PLAIN)
            .setInt(CONTENT_LENGTH, response.content().readableBytes());
        return Futures.immediateFuture(response);
    };

    private static BootstrapFactory FACTORY;
    private static String localAddress;

    @Mock
    private HttpServerStackGrouping serverConfig;
    @Mock
    private HttpClientStackGrouping clientConfig;
    @Mock
    private TransportChannelListener serverTransportListener;
    @Mock
    private TransportChannelListener clientTransportListener;

    private ServerSocket socket;
    private int localPort;

    @BeforeAll
    static void beforeAll() {
        FACTORY = new BootstrapFactory("IntegrationTest", 0);
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
    }

    @AfterAll
    static void afterAll() {
        FACTORY.close();
    }

    @BeforeEach
    void beforeEach() throws IOException {
        // find free port
        socket = new ServerSocket(0);
        localPort = socket.getLocalPort();
        socket.close();
    }

    @Test
    void noAuthTcp() throws Exception {
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort)).when(clientConfig).getTransport();
        integrationTest();
    }

    @Test
    void basicAuthTcp() throws Exception {
        doReturn(serverTransportTcp(localAddress, localPort, USER_HASHES_MAP))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort, USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest();
    }

    @Test
    void noAuthTls() throws Exception {
        final var certData = generateX509CertData("RSA");
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey()))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate())).when(clientConfig).getTransport();
        integrationTest();
    }

    @Test
    void basicAuthTls() throws Exception {
        final var certData = generateX509CertData("EC");
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey(),
            USER_HASHES_MAP)).when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest();
    }

    void integrationTest() throws Exception {
        final var server = HTTPServer.listen(serverTransportListener, FACTORY.newServerBootstrap(),
            serverConfig, DISPATCHER).get(2, TimeUnit.SECONDS);
        try {
            final var client = HTTPClient.connect(clientTransportListener, FACTORY.newBootstrap(), clientConfig)
                .get(2, TimeUnit.SECONDS);
            try {
                verify(serverTransportListener, timeout(1000)).onTransportChannelEstablished(any());
                verify(clientTransportListener, timeout(1000)).onTransportChannelEstablished(any());

                for (var method : METHODS) {
                    final var uri = nextValue("URI");
                    final var payload = nextValue("PAYLOAD");
                    final var request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.valueOf(method),
                        uri, wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
                    request.headers().set(CONTENT_TYPE, TEXT_PLAIN)
                        .setInt(CONTENT_LENGTH, request.content().readableBytes())
                        // keep connection alive to allow multiple requests on same connections
                        .set(CONNECTION, KEEP_ALIVE);

                    final var response = client.invoke(request).get(1, TimeUnit.SECONDS);
                    assertNotNull(response);
                    assertEquals(OK, response.status());
                    final var expected = RESPONSE_TEMPLATE.formatted(method, uri, payload);
                    assertEquals(expected, response.content().toString(StandardCharsets.UTF_8));
                }
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static String nextValue(final String prefix) {
        return prefix + COUNTER.incrementAndGet();
    }

    public static X509CertData generateX509CertData(final String algorithm) throws Exception {
        final var keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        if (isRSA(algorithm)) {
            keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), SECURE_RANDOM);
        } else {
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), SECURE_RANDOM);
        }
        final var keyPair = keyPairGenerator.generateKeyPair();
        final var certificate = generateCertificate(keyPair, isRSA(algorithm) ? "SHA256withRSA" : "SHA256withECDSA");
        return new X509CertData(certificate, keyPair.getPrivate());
    }

    private static X509Certificate generateCertificate(final KeyPair keyPair, final String hashAlgorithm)
            throws Exception {
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());

        final var x500Name = new X500Name("CN=TestCertificate");
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
            x500Name,
            keyPair.getPublic());
        return new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
    }

    private static boolean isRSA(final String algorithm) {
        return "RSA".equals(algorithm);
    }

    private record X509CertData(X509Certificate certificate, PrivateKey privateKey) {
    }
}

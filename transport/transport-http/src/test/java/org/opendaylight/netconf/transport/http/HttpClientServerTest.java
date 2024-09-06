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
import static org.opendaylight.netconf.transport.http.TestUtils.freePort;
import static org.opendaylight.netconf.transport.http.TestUtils.generateX509CertData;
import static org.opendaylight.netconf.transport.http.TestUtils.invoke;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;

@ExtendWith(MockitoExtension.class)
class HttpClientServerTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";
    private static final Map<String, String> USER_HASHES_MAP = Map.of(USERNAME, "$0$" + PASSWORD);
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String[] METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};
    private static final String RESPONSE_TEMPLATE = "Method: %s URI: %s Payload: %s";

    private static final AuthHandlerFactory CUSTOM_AUTH_HANDLER_FACTORY =
        () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return USERNAME.equals(username) && PASSWORD.equals(password) ? username : null;
            }
        };

    private static ScheduledExecutorService scheduledExecutor;
    private static RequestDispatcher requestDispatcher;
    private static BootstrapFactory bootstrapFactory;
    private static String localAddress;

    @Mock
    private HttpServerStackGrouping serverConfig;
    @Mock
    private HttpClientStackGrouping clientConfig;
    @Mock
    private TransportChannelListener serverTransportListener;
    @Mock
    private TransportChannelListener clientTransportListener;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("IntegrationTest", 0);
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        requestDispatcher = (request, callback) -> {
            // emulate asynchronous server request processing - run in separate thread with 100 millis delay
            scheduledExecutor.schedule(() -> {
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
                callback.onSuccess(response);
            }, 100, TimeUnit.MILLISECONDS);
        };
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        scheduledExecutor.shutdown();
    }

    @ParameterizedTest(name = "TCP with no authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void noAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort)).when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TCP with Basic authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void basicAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort, USER_HASHES_MAP))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort, USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TCP with custom auth handler factory, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void customAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort, USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2, CUSTOM_AUTH_HANDLER_FACTORY);
    }

    @ParameterizedTest(name = "TLS with no authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void noAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("RSA");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey()))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate())).when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TLS with Basic authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void basicAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("EC");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey(),
            USER_HASHES_MAP)).when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TLS with custom auth handler factory, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void customAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("RSA");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey()))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2, CUSTOM_AUTH_HANDLER_FACTORY);
    }

    private void integrationTest(final boolean http2) throws Exception {
        integrationTest(http2, null);
    }

    private void integrationTest(final boolean http2, final AuthHandlerFactory authHandlerFactory) throws Exception {
        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig, requestDispatcher, authHandlerFactory).get(2, TimeUnit.SECONDS);
        try {
            final var client = HTTPClient.connect(clientTransportListener, bootstrapFactory.newBootstrap(),
                    clientConfig, http2).get(2, TimeUnit.SECONDS);
            try {
                verify(serverTransportListener, timeout(2000)).onTransportChannelEstablished(any());
                verify(clientTransportListener, timeout(2000)).onTransportChannelEstablished(any());

                for (var method : METHODS) {
                    final var uri = nextValue("URI");
                    final var payload = nextValue("PAYLOAD");
                    final var request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.valueOf(method),
                        uri, wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
                    request.headers().set(CONTENT_TYPE, TEXT_PLAIN)
                        .setInt(CONTENT_LENGTH, request.content().readableBytes())
                        // allow multiple requests on same connections
                        .set(CONNECTION, KEEP_ALIVE);

                    final var response = invoke(client, request).get(2, TimeUnit.SECONDS);
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

    @ParameterizedTest(name = "Java HttpClient compatibility check, Basic Auth: {0}")
    @ValueSource(booleans = {false, true})
    @Timeout(20)
    @SuppressWarnings("RegexpSinglelineJava")
    void cleartextUpgradeFlowCompatibility(final boolean withAuth) throws Exception {

        // FIXME https://lf-opendaylight.atlassian.net/browse/NETCONF-1378

        // validate server cleartext protocol upgrade flow being compatible with java.net.HttpClient
        final var localPort = freePort();
        final var transport = withAuth
            ? serverTransportTcp(localAddress, localPort, USER_HASHES_MAP)
            : serverTransportTcp(localAddress, localPort);
        doReturn(transport).when(serverConfig).getTransport();

        final var uri = nextValue("URI");
        final var payload = nextValue("PAYLOAD");
        final var url = URI.create("http://%s:%d/%s".formatted(localAddress, localPort, uri));

        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig, requestDispatcher).get(2, TimeUnit.SECONDS);
        try {
            try (var client = HttpClient.newBuilder()
                // authenticator is only used if only 401 returned
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                    }
                })
                .build()) {

                final var request = HttpRequest.newBuilder(url)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "text-plain")
                    .header("Accept", "text-plain")
                    .build();
                final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertNotNull(response);
                assertEquals(200, response.statusCode());
                final var expected = RESPONSE_TEMPLATE.formatted("GET", uri, payload);
                assertEquals(expected, response.body());
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static String nextValue(final String prefix) {
        return prefix + COUNTER.incrementAndGet();
    }
}

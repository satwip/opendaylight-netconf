/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.jaxrs.JaxRsLocationProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractE2ETest extends AbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractE2ETest.class);
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;

    protected static final int STATUS_MISSING = ERROR_TAG_MAPPING.statusOf(ErrorTag.DATA_MISSING).code();
    protected static final String APPLICATION_JSON = "application/json";

    private static BootstrapFactory bootstrapFactory;

    private NettyEndpoint endpoint;
    private HttpClientStackGrouping clientStackGrouping;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        final var localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        final var port = freePort();
        final var serverBaseUri = URI.create("http://" + localAddress + ":" + port + "/rests");

        setup();
        final var domDataBroker = getDomBroker();
        final var schemaContext = getRuntimeContext().modelContext();
        final var securityManager = new DefaultWebSecurityManager(new AuthenticatingRealm() {
            @Override
            protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
                    throws AuthenticationException {
                final var principal = ((String) token.getPrincipal());
                final var credentials = new String(((char[]) token.getCredentials()));
                if ("admin".equals(principal) && "password".equals(credentials)) {
                    return new SimpleAuthenticationInfo(principal, credentials, "user");
                }
                return null;
            }
        });
        final var principalService = new AAAShiroPrincipalService(securityManager);
        final var serverTransport = ConfigUtils.serverTransportTcp(localAddress, port);
        final var serverStackGrouping = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return serverTransport;
            }
        };
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            serverBaseUri, "restconf-netty-e2e", 8,
            NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping);

        final var schemaService = new FixedDOMSchemaService(schemaContext);
        final var dataBindProvider = new MdsalDatabindProvider(schemaService);
        final var rpcService = new RouterDOMRpcService(new DOMRpcRouter(schemaService));
        final var actionService = new RouterDOMActionService(new DOMRpcRouter(schemaService));
        final var mountPointService = new DOMMountPointServiceImpl();

        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, rpcService, actionService,
            mountPointService);
        final var registry = new MdsalRestconfStreamRegistry(new JaxRsLocationProvider(), domDataBroker);
        endpoint = new NettyEndpoint(server, principalService, registry, configuration);

        final var clientransport = ConfigUtils.clientTransportTcp(localAddress, port, "admin", "password");
        clientStackGrouping = new HttpClientStackGrouping() {
            @Override
            public @NonNull Class<? extends DataContainer> implementedInterface() {
                return HttpClientStackGrouping.class;
            }

            @Override
            public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.Transport getTransport() {
                return clientransport;
            }
        };
    }

    private static int freePort() {
        // find free port
        try {
            final var socket = new ServerSocket(0);
            final var localPort = socket.getLocalPort();
            socket.close();
            return localPort;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @AfterEach
    void afterEach() {
        endpoint.deactivate();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri) throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content));
    }

    private FullHttpResponse invokeRequest(final FullHttpRequest request) throws Exception {
        final var channelListener = new TestTransportListener(SseUtils::enableClientSse);
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(() -> channelListener.initialized);
        final var callback = new RequestCallback();
        client.invoke(request, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(() -> callback.completed);
        client.shutdown().get(2, TimeUnit.SECONDS);
        assertNotNull(callback.response);
        return callback.response;
    }

    private static FullHttpRequest buildRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        request.headers().set(HttpHeaderNames.ACCEPT, mediaType);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
        if (method != HttpMethod.GET) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        return request;
    }

    private static final class RequestCallback implements FutureCallback<FullHttpResponse> {
        private volatile boolean completed;

        private volatile FullHttpResponse response;

        @Override
        public void onSuccess(final FullHttpResponse result) {
            // detach response object from channel, so message content is not lost after client is disconnected
            final var content = Unpooled.wrappedBuffer(ByteBufUtil.getBytes(result.content()));
            final var copy = new DefaultFullHttpResponse(result.protocolVersion(), result.status(), content);
            copy.headers().set(result.headers());
            this.response = copy;
            this.completed = true;
        }

        @Override
        public void onFailure(final @NonNull Throwable throwable) {
            this.completed = true;
            throw new IllegalStateException(throwable);
        }
    }

    @SuppressWarnings("RegexpSinglelineJava")
    protected void assertJsonContent(final String getRequestUri, final String expectedJson) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, getRequestUri);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var content = response.content().toString(StandardCharsets.UTF_8);
        // TODO compare json content taking into account namespaces
        System.out.println("Accepted:\n" + content);
        System.out.println("Expected:\n" + expectedJson);
        JSONAssert.assertEquals(expectedJson, content, JSONCompareMode.LENIENT);
    }

    private static final class TestTransportListener implements TransportChannelListener {
        private final Consumer<Channel> initializer;

        private volatile boolean initialized;

        TestTransportListener(final Consumer<Channel> initializer) {
            this.initializer = initializer;
        }

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            initializer.accept(channel.channel());
            initialized = true;
        }

        @Override
        public void onTransportChannelFailed(final @NonNull Throwable cause) {
            throw new IllegalStateException("HTTP connection failure", cause);
        }
    }
}

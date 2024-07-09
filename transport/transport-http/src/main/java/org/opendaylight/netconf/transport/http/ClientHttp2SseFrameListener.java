/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.processChunks;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side Server-Sent Event (SSE) HTTP/2 frame listener implementation.
 *
 * <p>
 * Acts as alternative inbound stream processor invoked by {@link Http2ToHttpAdapter}. Intercepts header and data
 * frames for associated stream-id.
 *
 * <p>
 * The expected header frame with response status {@code OK} and {@code content-type=text/event-stream} header
 * indicates the stream request being accepted by the server (stream beginning), then subsequent data frames are
 * treated as event messages. Other headers are treated as server request decline, following data frame is treated
 * as error message.
 *
 */
final class ClientHttp2SseFrameListener extends Http2FrameAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHttp2SseFrameListener.class);

    private final String uri;
    private final FutureCallback<Registration> requestCallback;
    private final EventStreamListener listener;
    private final Runnable onClose;
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final AtomicBoolean awaitingErrorMessage = new AtomicBoolean(false);

    ClientHttp2SseFrameListener(final String uri, final EventStreamListener listener,
            final FutureCallback<Registration> requestCallback, final Runnable onClose) {
        this.uri = uri;
        this.listener = requireNonNull(listener);
        this.requestCallback = requestCallback;
        this.onClose = onClose;
    }

    EventStreamListener listener() {
        return listener;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
        final int padding, final boolean endStream) throws Http2Exception {
        LOG.info("onHeadersRead(1) -> headers={}, eos={}", headers, endStream);
        onHeadersRead(headers, endStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
        final int streamDependency, final short weight, final boolean exclusive, final int padding,
        final boolean endStream) throws Http2Exception {
        LOG.info("onHeadersRead(2) -> headers={}, eos={}", headers, endStream);
        onHeadersRead(headers, endStream);
    }

    private void onHeadersRead(final Http2Headers headers, final boolean endStream) throws Http2Exception {
        if (streaming.get()) {
            LOG.error("Unexpected headers while data stream is expected. {}", headers);
            onClose();
            return;
        }
        final var status = HttpConversionUtil.parseStatus(headers.status());
        if (HttpResponseStatus.OK.equals(status)
            && headers.contains(HttpHeaderNames.CONTENT_TYPE, SseUtils.TEXT_EVENT_STREAM, true)) {
            // stream request accepted
            streaming.set(true);
            listener.onStreamStart();
            // notify SSE request succeeded with Registration (Closable)
            // TODO: notify server to close stream (require server side frame listener)
            requestCallback.onSuccess(ClientHttp2SseFrameListener.this::onClose);
        } else {
            // stream request declined
            if (endStream) {
                // no error message, use status
                requestCallback.onFailure(new IllegalStateException("Error: " + status));
                onClose();
            } else {
                // error message to be delivered with data frame
                awaitingErrorMessage.set(true);
            }
        }
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding,
        final boolean endOfStream) throws Http2Exception {
        final var readableBytes = data.readableBytes();
        if (awaitingErrorMessage.get()) {
            // decline response body
            final var errorMessage = new String(ByteBufUtil.getBytes(data.slice()), StandardCharsets.UTF_8);
            requestCallback.onFailure(new IllegalStateException(errorMessage));
            onClose();

        } else if (streaming.get()) {
            // data chunk
            processChunks(data, listener);
            if (endOfStream) {
                // end stream
                LOG.info("SSE stream ended for URI={}", uri);
                listener.onStreamEnd();
                onClose();
            }
        } else {
            LOG.error("Unexpected data frame ({} bytes) when headers are expected.", readableBytes);
            onClose();
        }
        return padding + readableBytes;
    }

    private void onClose() {
        if (onClose != null) {
            onClose.run();
        }
    }
}

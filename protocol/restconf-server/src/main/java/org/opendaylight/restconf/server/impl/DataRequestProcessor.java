/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.ContentTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.impl.ContentTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.impl.PathParameters.DATA;
import static org.opendaylight.restconf.server.impl.RequestUtils.childBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.dataPostBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.extractApiPath;
import static org.opendaylight.restconf.server.impl.RequestUtils.serverRequest;
import static org.opendaylight.restconf.server.impl.ResponseUtils.responseBuilder;
import static org.opendaylight.restconf.server.impl.ResponseUtils.simpleResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static processor implementation serving {@code /data} path requests.
 */
final class DataRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DataRequestProcessor.class);

    private DataRequestProcessor() {
        // hidden on purpose
    }

    static void process(final RequestParameters params, final RestconfServer service,
            final FutureCallback<FullHttpResponse> callback) {
        final var method = params.method();
        final var contentType = params.contentType();
        final var apiPath = extractApiPath(params);

        if (HttpMethod.GET.equals(method)) {
            // GET /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataGET(getRequest(params, callback));
            } else {
                service.dataGET(getRequest(params, callback), apiPath);
            }

        } else if (HttpMethod.POST.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // POST /data(/.+)?
            if (apiPath.isEmpty()) {
                service.dataPOST(postRequest(params, callback), childBody(params));
            } else {
                service.dataPOST(postRequest(params, callback), apiPath, dataPostBody(params));
            }

        } else if (HttpMethod.PUT.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // FIXME implement
            callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));

        } else if (HttpMethod.PATCH.equals(method) && RESTCONF_TYPES.contains(contentType)) {
            // PATCH /data(/.*)? RESTCONF patch case
            // FIXME implement
            callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));

        } else if (HttpMethod.PATCH.equals(method) && YANG_PATCH_TYPES.contains(contentType)) {
            // PATCH /data (yang-patch case)
            // FIXME implement
            callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));

        } else if (HttpMethod.DELETE.equals(method)) {
            // DELETE /data/.*
            // FIXME implement
            callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_IMPLEMENTED));

        } else {
            callback.onSuccess(simpleResponse(params, HttpResponseStatus.NOT_FOUND));
        }
    }

    private static ServerRequest<DataGetResult> getRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result ->
            responseBuilder(params, HttpResponseStatus.OK)
                .setHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
                .setMetadataHeaders(result).setBody(result.body()).build());
    }

    private static <T extends DataPostResult> ServerRequest<T> postRequest(final RequestParameters params,
            final FutureCallback<FullHttpResponse> callback) {
        return serverRequest(params, callback, result -> {
            if (result instanceof CreateResourceResult createResult) {
                final var location = params.basePath() + DATA + "/" + createResult.createdPath();
                return responseBuilder(params, HttpResponseStatus.CREATED)
                    .setHeader(HttpHeaderNames.LOCATION, location)
                    .setMetadataHeaders(createResult).build();
            }
            if (result instanceof InvokeResult invokeResult) {
                final var output = invokeResult.output();
                return output == null ? simpleResponse(params, HttpResponseStatus.NO_CONTENT)
                    : responseBuilder(params, HttpResponseStatus.OK).setBody(output).build();
            }
            LOG.error("Unhandled result {}", result);
            return simpleResponse(params, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        });
    }
}

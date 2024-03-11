/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.ContentTypes.ALL_TYPES;
import static org.opendaylight.restconf.server.impl.ContentTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.impl.ContentTypes.YANG_PATCH_TYPES;
import static org.opendaylight.restconf.server.impl.RequestUtils.apiPath;
import static org.opendaylight.restconf.server.impl.RequestUtils.childBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.dataPostBody;
import static org.opendaylight.restconf.server.impl.RequestUtils.serverRequest;
import static org.opendaylight.restconf.server.impl.ResponseUtils.callback;
import static org.opendaylight.restconf.server.impl.ResponseUtils.setResponse;
import static org.opendaylight.restconf.server.impl.ResponseUtils.setStatusOnlyResponse;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DataRequestMapping implements RequestMapping {

    private static final Logger LOG = LoggerFactory.getLogger(DataRequestMapping.class);
    private static final String DATA = "/data";
    private static final String DATA_WITH_ID = "/data/(.+)";

    private static final List<? extends AbstractRequestProcessor> PROCESSORS = List.of(

        // GET /data(/.+)?
        new AbstractRequestProcessor(DATA, DATA_WITH_ID, HttpMethod.GET, ALL_TYPES) {
            @Override
            public void process(final RestconfServer service, final RequestContext context) {
                final var apiPath = apiPath(apiPathPattern, context);
                final var serverRequest = serverRequest(context);
                service.dataGET(serverRequest, apiPath)
                    .addCallback(callback(context, result ->
                        setResponse(context, result, DataGetResult::body, serverRequest.prettyPrint())));
            }
        },

        // POST /data(/.+)?
        new AbstractRequestProcessor(DATA, DATA_WITH_ID, HttpMethod.POST, RESTCONF_TYPES) {
            @Override
            public void process(final RestconfServer service, final RequestContext context) {
                final var apiPath = apiPath(apiPathPattern, context);
                final var serverRequest = serverRequest(context);
                final RestconfCallback<DataPostResult> callback = callback(context, result -> {
                    if (result instanceof CreateResourceResult createResult) {
                        setResponse(context, createResult, HttpResponseStatus.CREATED);
                        // FIXME url header
//                            final var builder = Response.created(uriInfo.getBaseUriBuilder()
//                                .path("data")
//                                .path(createResource.createdPath().toString())
//                                .build());
                    } else if (result instanceof InvokeResult invokeResult) {
                        setResponse(context, invokeResult, InvokeResult::output, serverRequest.prettyPrint());
                    } else {
                        LOG.error("Unhandled result {}", result);
                        setStatusOnlyResponse(context, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    }
                });
                if (apiPath == null) {
                    service.dataPOST(serverRequest, childBody(context)).addCallback(callback);
                } else {
                    service.dataPOST(serverRequest, apiPath, dataPostBody(context)).addCallback(callback);
                }
            }
        },

        // PUT /data(/.*)?
        new AbstractRequestProcessor(DATA, DATA_WITH_ID, HttpMethod.PUT, RESTCONF_TYPES) {
            @Override
            public void process(final RestconfServer restconfServer, final RequestContext context) {
                // FIXME implement
                setStatusOnlyResponse(context, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        },

        // PATCH /data(/.*)?
        new AbstractRequestProcessor(DATA, DATA_WITH_ID, HttpMethod.PATCH, RESTCONF_TYPES) {
            @Override
            public void process(final RestconfServer restconfServer, final RequestContext context) {
                // FIXME implement
                setStatusOnlyResponse(context, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        },

        // DELETE /data/.*
        new AbstractRequestProcessor(DATA, DATA_WITH_ID, HttpMethod.DELETE, ALL_TYPES) {
            @Override
            public void process(final RestconfServer restconfServer, final RequestContext context) {
                // FIXME implement
                setStatusOnlyResponse(context, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        },

        // PATCH /data (yang-patch case)
        new AbstractRequestProcessor(DATA, null, HttpMethod.PATCH, YANG_PATCH_TYPES) {
            @Override
            public void process(final RestconfServer restconfServer, final RequestContext context) {
                // FIXME implement
                setStatusOnlyResponse(context, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        }

        // FIXME implement operations
    );

    static final DataRequestMapping INSTANCE = new DataRequestMapping();

    private DataRequestMapping() {
        // singleton
    }

    @Override
    public RequestProcessor findMatching(RequestContext context) {
        return PROCESSORS.stream().filter(processor -> processor.matches(context)).findFirst().orElse(null);
    }
}

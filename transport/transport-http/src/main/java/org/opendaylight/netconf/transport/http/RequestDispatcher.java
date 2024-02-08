/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Functional interface for HTTP request dispatcher.
 */
@FunctionalInterface
public interface RequestDispatcher {

    /**
     * Performs {@link FullHttpRequest} processing. Any error occurred is expected either to be returned within
     * {@link FullHttpResponse} with appropriate HTTP status code or set as future cause.
     *
     * @param request http request
     * @return future providing http response or cause in case of error.
     */
    ListenableFuture<FullHttpResponse> dispatch(FullHttpRequest request);
}

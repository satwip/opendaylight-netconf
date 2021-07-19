/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Realm;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Execution implements Callable<Void> {

    private final ArrayList<Request> payloads;
    private final AsyncHttpClient asyncHttpClient;
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);
    private final boolean invokeAsync;
    private final Semaphore semaphore;
    private final int throttle;

    static final class DestToPayload {

        private final String destination;
        private final String payload;

        DestToPayload(final String destination, final String payload) {
            this.destination = destination;
            this.payload = payload;
        }

        public String getDestination() {
            return destination;
        }

        public String getPayload() {
            return payload;
        }
    }

    public Execution(final TesttoolParameters params, final ArrayList<DestToPayload> payloads) {
        this.invokeAsync = params.async;
        this.throttle = params.throttle / params.threadAmount;

        if (params.async && params.threadAmount > 1) {
            LOG.info("Throttling per thread: {}", this.throttle);
        }
        this.semaphore = new Semaphore(this.throttle);

        this.asyncHttpClient = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .build());

        this.payloads = new ArrayList<>();
        for (DestToPayload payload : payloads) {
            BoundRequestBuilder requestBuilder = asyncHttpClient.preparePost(payload.getDestination())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .setBody(payload.getPayload())
                    .setRequestTimeout(Integer.MAX_VALUE);

            requestBuilder.setRealm(new Realm.Builder(params.controllerAuthUsername, params.controllerAuthPassword)
                    .setScheme(Realm.AuthScheme.BASIC)
                    .setMethodName("POST")
                    .setUsePreemptiveAuth(true)
                    .build());

            this.payloads.add(requestBuilder.build());
        }
    }

    private void invokeSync() {
        LOG.info("Begin sending sync requests");
        for (Request request : payloads) {
            try {
                Response response = asyncHttpClient.executeRequest(request).get();
                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    if (response.getStatusCode() == 409) {
                        LOG.warn("Request failed, status code: {} - one or more of the devices"
                                + " is already configured, skipping the whole batch", response.getStatusCode());
                    } else {
                        LOG.warn("Status code: {}", response.getStatusCode());
                        LOG.warn("url: {}", request.getUrl());
                        LOG.warn("body: {}", response.getResponseBody());
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Failed to execute request", e);
            }
        }
        LOG.info("End sending sync requests");
    }

    private void invokeAsync() {
        LOG.info("Begin sending async requests");

        for (final Request request : payloads) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {
                @Override
                public State onStatusReceived(final HttpResponseStatus status) throws Exception {
                    super.onStatusReceived(status);
                    if (status.getStatusCode() != 200 && status.getStatusCode() != 204) {
                        if (status.getStatusCode() == 409) {
                            LOG.warn("Request failed, status code: {} - one or more of the devices"
                                    + " is already configured, skipping the whole batch", status.getStatusCode());
                        } else {
                            LOG.warn("Request failed, status code: {}",
                                status.getStatusCode() + status.getStatusText());
                            LOG.warn("request: {}", request.toString());
                        }
                    }
                    return State.CONTINUE;
                }

                @Override
                public Response onCompleted(final Response response) {
                    semaphore.release();
                    return response;
                }
            });
        }
        LOG.info("Requests sent, waiting for responses");

        try {
            semaphore.acquire(this.throttle);
        } catch (InterruptedException e) {
            LOG.warn("Semaphore acquire interrupted");
        }

        LOG.info("Responses received, ending...");
    }

    @Override
    public Void call() {
        if (invokeAsync) {
            this.invokeAsync();
        } else {
            this.invokeSync();
        }
        return null;
    }
}

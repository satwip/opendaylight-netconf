/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

class OperationsE2ETest extends AbstractE2ETest {

    @Test
    void readAllOperationsTest() throws Exception {
        final var result = invokeRequest(HttpMethod.GET, "/rests/operations", APPLICATION_JSON);
        assertEquals(200, result.status().code());
    }

    @Test
    void readOperationTest() throws Exception {
        final var result = invokeRequest(HttpMethod.GET,
            "/rests/operations/odl-device-notification:subscribe-device-notification",
            APPLICATION_JSON);
        assertEquals(200, result.status().code());
    }

    @Test
    @SuppressWarnings("checkstyle:lineLength")
    void invokeOperationTest() throws Exception {
        final var result = invokeRequest(HttpMethod.POST,
            "/rests/operations/odl-device-notification:subscribe-device-notification",
            APPLICATION_JSON,
            """
                {
                    "input": {
                        "path":"/network-topology:network-topology/topology[topology-id='topology-netconf']/node[node-id='test_device']"
                    }
                }""");
        assertEquals(200, result.status().code());
    }

    @Test
    void errorHandlingTest() {
        // TODO
    }

    @Test
    void operationsOptionsTest() throws Exception {
        final var result = invokeRequest(HttpMethod.OPTIONS, "/rests/operations");
        assertEquals(200, result.status().code());
    }

    @Test
    void operationsHeadTest() throws Exception {
        final var result = invokeRequest(HttpMethod.HEAD,"/rests/operations");
        assertEquals(200, result.status().code());
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.swagger.OpenApiObject;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class ListPostRequestsTest {
    private static OpenApiObject doc;

    @BeforeClass
    public static void startUp() throws Exception {
        final var context = YangParserTestUtils.parseYang("""
            module list-post {
              namespace "list-post";
              prefix lp;
                container container {
                  list list {
                    key "name address";
                    leaf name {
                      type string;
                    }
                    leaf address {
                      type string;
                    }
                  }
                }
              }""");
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new ApiDocGeneratorRFC8040(schemaService);
        final var uriInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        doc = (OpenApiObject) generator.getApiDeclaration("list-post", null, uriInfo, ApiDocServiceImpl.OAversion.V3_0);
        assertNotNull(doc);
    }

    /**
     * Test to verify that we do NOT generate OpenApi example POST request for path ending with list element.
     *
     * <p>
     * Assert that for paths ending with container we have examples for all types of requests.
     * Assert that for paths ending with list we do NOT have POST example.
     */
    @Test
    public void testListPostRequest() {
        // for container, we have both post (with child as payload) and put (with itself as payload)
        final var pathToContainer = "/rests/data/list-post:container";
        assertNotNull(doc.getPaths().get(pathToContainer).get("get"));
        assertNotNull(doc.getPaths().get(pathToContainer).get("post"));
        assertNotNull(doc.getPaths().get(pathToContainer).get("put"));
        assertNotNull(doc.getPaths().get(pathToContainer).get("patch"));
        assertNotNull(doc.getPaths().get(pathToContainer).get("delete"));
        // for list, we cannot make a post request
        final var pathToList = "/rests/data/list-post:container/list={name},{address}";
        assertNotNull(doc.getPaths().get(pathToList).get("get"));
        assertNull(doc.getPaths().get(pathToList).get("post"));
        assertNotNull(doc.getPaths().get(pathToList).get("put"));
        assertNotNull(doc.getPaths().get(pathToList).get("patch"));
        assertNotNull(doc.getPaths().get(pathToList).get("delete"));
    }
}

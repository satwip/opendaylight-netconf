/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.json.to.nn.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.sal.rest.impl.test.providers.AbstractBodyReaderTest;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class JsonLeafrefToNnTest extends AbstractBodyReaderTest {

    private final JsonNormalizedNodeBodyReader jsonBodyReader;
    private static EffectiveModelContext schemaContext;

    public JsonLeafrefToNnTest() {
        super(schemaContext, null);
        this.jsonBodyReader = new JsonNormalizedNodeBodyReader(controllerContext);
    }

    @BeforeClass
    public static void initialize() {
        schemaContext = schemaContextLoader("/json-to-nn/leafref", schemaContext);
    }

    @Test
    public void jsonIdentityrefToNormalizeNode() throws Exception {

        final String uri = "leafref-module:cont";
        mockBodyReader(uri, this.jsonBodyReader, false);
        final InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/leafref/json/data.json");

        final NormalizedNodeContext normalizedNodeContext = this.jsonBodyReader.readFrom(
                null, null, null, this.mediaType, null, inputStream);

        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());
        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("lf2 121"));
    }

    @Override
    protected MediaType getMediaType() {
        return null;
    }

}

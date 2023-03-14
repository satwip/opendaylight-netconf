/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Test;
import org.opendaylight.restconf.openapi.AbstractOpenApiTest;
import org.opendaylight.yangtools.yang.common.Revision;

public final class DefinitionGeneratorTest extends AbstractOpenApiTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testConvertToJsonSchema() throws IOException {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final var result = DefinitionGenerator.getSchema(module, CONTEXT, new DefinitionNames(), true, MAPPER);
        assertNotNull(result);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final var result = DefinitionGenerator.getSchema(module, CONTEXT, new DefinitionNames(), true, MAPPER);
        assertNotNull(result);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        final var result = DefinitionGenerator.getSchema(module, CONTEXT, new DefinitionNames(), true, MAPPER);
        assertNotNull(result);
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.schema.RestconfSchemaService;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfSchemaServiceTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    private SchemaContext schemaContext;

    private SchemaContextHandler contextHandler;
    RestconfSchemaService schemaService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        contextHandler.onGlobalContextUpdated(schemaContext);

        when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    /**
     * Test if service was successfully created
     */
    @Test
    public void schemaServiceImplInitTest() {
        assertNotNull(schemaService);
    }

    /**
     * Get schema and find existing module
     */
    @Test
    public void getSchemaTest() {
        final SchemaExportContext exportContext = schemaService.getSchema(TEST_MODULE);
        assertNotNull(exportContext);

        final Module module = exportContext.getModule();
        assertNotNull("Existing module should be found", module);

        assertEquals("module1", module.getName());
        assertEquals("2014-01-01", SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("module:1", module.getNamespace().toString());
    }

    /**
     * Get schema, but llok for not-existong module
     */
    @Test
    public void getSchemaNegativeTest() {
        final SchemaExportContext exportContext = schemaService.getSchema(NOT_EXISTING_MODULE);

        assertNotNull(exportContext);
        assertNull("Not-existing module should not be found", exportContext.getModule());
    }
}

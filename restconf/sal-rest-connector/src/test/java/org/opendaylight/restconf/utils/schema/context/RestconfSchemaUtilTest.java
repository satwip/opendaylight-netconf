/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.schema.context;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class RestconfSchemaUtilTest {
    private SchemaContext schemaContext;
    private org.opendaylight.yangtools.yang.model.api.Module restconfModule;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        restconfModule = schemaContext.findModuleByName(Draft11.RestconfModule.NAME,
                SimpleDateFormatUtil.getRevisionFormat().parse(Draft11.RestconfModule.REVISION));
    }

    @Test
    public void getRestconfSchemaNodeModuleTest() {
        DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE);
        assertNotNull("Module list schema node should be found", dataSchemaNode);
    }

    @Test
    public void getRestconfSchemaNodeStreamTest() {
        DataSchemaNode dataSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
        assertNotNull("Stream list schema node should be found", dataSchemaNode);
    }

    @Test(expected = AbstractMethodError.class)
    public void getRestconfSchemaNodeNegativeTest() {
        RestconfSchemaUtil.getRestconfSchemaNode(restconfModule, "");
    }

    @Test
    public void findSchemaNodeInCollectionTest() {
        SchemaNode schemaNode = RestconfSchemaUtil.findSchemaNodeInCollection(restconfModule.getGroupings(),
                Draft11.RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE);
        assertNotNull("Restconf grouping schema node should be found", schemaNode);
    }

    @Test(expected = AbstractMethodError.class)
    public void findSchemaNodeInCollectionNegativeTest() {
        RestconfSchemaUtil.findSchemaNodeInCollection(restconfModule.getGroupings(), "");
    }
}

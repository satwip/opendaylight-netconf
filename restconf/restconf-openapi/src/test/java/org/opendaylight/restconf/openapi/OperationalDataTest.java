/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class OperationalDataTest {
    private static final String DATA_MP_URI = "/rests/data/nodes/node=123/yang-ext:mount";
    private static final String OPERATIONS_MP_URI = "/rests/operations/nodes/node=123/yang-ext:mount";
    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
        "action-types_container",
        "action-types_list",
        "action-types_multi-container",
        "action-types_container-action_input",
        "action-types_container-action_output",
        "action-types_list-action_output",
        "action-types_list-action_input",
        "action-types_multi-container_inner-container",
        "operational_root",
        "operational_root_config-container",
        "operational_root_config-container_config-container-oper-list",
        "operational_root_oper-container",
        "operational_root_oper-container_config-container",
        "operational_root_oper-container_oper-container-list");

    private static final Set<String> EXPECTED_PATHS = Set.of(
        OPERATIONS_MP_URI + "/action-types:list={name}/list-action",
        OPERATIONS_MP_URI + "/action-types:container/container-action",
        OPERATIONS_MP_URI + "/action-types:multi-container/inner-container/action",
        OPERATIONS_MP_URI,
        DATA_MP_URI + "/action-types:list={name}",
        DATA_MP_URI + "/operational:root",
        DATA_MP_URI + "/operational:root/oper-container/config-container",
        DATA_MP_URI + "/operational:root/oper-container/oper-container-list={oper-container-list-leaf}",
        DATA_MP_URI + "/action-types:multi-container",
        DATA_MP_URI + "/action-types:multi-container/inner-container",
        DATA_MP_URI + "/operational:root/oper-container",
        DATA_MP_URI + "/action-types:container",
        DATA_MP_URI + "/operational:root/config-container/config-container-oper-list={oper-container-list-leaf}",
        DATA_MP_URI + "/operational:root/config-container",
        DATA_MP_URI);
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private OpenApiObject mountPointApi;
    private Map<String, Schema> schemas;

    @Before
    public void before() throws Exception {
        final var schemaService = mock(DOMSchemaService.class);
        final var context = YangParserTestUtils.parseYangResourceDirectory("/operational");
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final var service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final var openApi = new MountPointOpenApiGeneratorRFC8040(schemaService, service).getMountPointOpenApi();
        openApi.onMountPointCreated(INSTANCE_ID);
        final var mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        mountPointApi = openApi.getMountPointApi(mockInfo, 1L, null);
        assertNotNull("Failed to find Datastore API", mountPointApi);
        schemas = mountPointApi.components().schemas();
    }

    @Test
    public void testOperationalPath() {
        final var paths = mountPointApi.paths();
        assertEquals(EXPECTED_PATHS, paths.keySet());
        for (final var path : paths.values()) {
            if (path.get() != null) {
                final var responses = path.get().responses();
                final var response = responses.values().iterator().next();
                final var content = response.content();
                // In case of 200 no content
                if (content != null) {
                    verifyOperationHaveCorrectXmlReference(content.get("application/xml").schema());
                    verifyOperationHaveCorrectJsonReference(content.get("application/json").schema());
                }
            }
            if (path.put() != null) {
                final var responses = path.put().requestBody();
                final var content = responses.content();
                verifyOperationHaveCorrectXmlReference(content.get("application/xml").schema());
                verifyOperationHaveCorrectJsonReference(content.get("application/json").schema());
            }
            if (path.post() != null) {
                final var responses = path.post().requestBody();
                final var content = responses.content();
                verifyOperationHaveCorrectXmlReference(content.get("application/xml").schema());
                verifyOperationHaveCorrectJsonReference(content.get("application/json").schema());
            }
            if (path.patch() != null) {
                final var responses = path.patch().requestBody();
                final var content = responses.content();
                verifyOperationHaveCorrectXmlReference(content.get("application/yang-data+xml").schema());
                verifyOperationHaveCorrectJsonReference(content.get("application/yang-data+json").schema());
            }
        }
    }

    @Test
    public void testOperationalSchema() {
        assertEquals(EXPECTED_SCHEMAS, schemas.keySet());
    }

    @Test
    public void testOperationalConfigRootSchemaProperties() {
        final var configRoot = schemas.get("operational_root");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("leaf-config", "config-container"), actualProperties);
    }

    @Test
    public void testOperationalConfigContOperListSchemaProperties() {
        final var configContOperList = schemas.get(
            "operational_root_config-container_config-container-oper-list");
        assertNotNull(configContOperList);
        final var actualProperties = configContOperList.properties().keySet();
        assertEquals(Set.of("oper-container-list-leaf"), actualProperties);
    }

    @Test
    public void testOperationalContListSchemaProperties() {
        final var operContList = schemas.get("operational_root_oper-container_oper-container-list");
        assertNotNull(operContList);
        final var actualProperties = operContList.properties().keySet();
        assertEquals(Set.of("oper-container-list-leaf"), actualProperties);
    }

    @Test
    public void testOperationalConConfigContSchemaProperties() {
        final var operConConfigCont = schemas.get("operational_root_oper-container_config-container");
        assertNotNull(operConConfigCont);
        final var actualProperties = operConConfigCont.properties().keySet();
        assertEquals(Set.of("config-container-config-leaf", "opconfig-container-oper-leaf"), actualProperties);
    }

    @Test
    public void testOperationalConfigContSchemaProperties() {
        final var configCont = schemas.get("operational_root_config-container");
        assertNotNull(configCont);
        final var actualProperties = configCont.properties().keySet();
        assertEquals(Set.of("config-container-config-leaf", "leaf-second-case"), actualProperties);
    }

    @Test
    public void testOperationalContSchemaProperties() {
        final var operCont = schemas.get("operational_root_oper-container");
        assertNotNull(operCont);
        final var actualProperties = operCont.properties().keySet();
        assertEquals(Set.of("config-container", "oper-container-list", "leaf-first-case", "oper-leaf-first-case",
            "oper-container-config-leaf-list"), actualProperties);
    }

    @Test
    public void testListActionSchemaProperties() {
        final var configRoot = schemas.get("action-types_list");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("name"), actualProperties);
    }

    @Test
    public void testListActionInputSchemaProperties() {
        final var configRoot = schemas.get("action-types_list-action_input");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("la-input"), actualProperties);
    }

    @Test
    public void testListActionOutputSchemaProperties() {
        final var configRoot = schemas.get("action-types_list-action_output");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("la-output"), actualProperties);
    }

    @Test
    public void testContainerActionSchemaProperties() {
        final var configRoot = schemas.get("action-types_container");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of(), actualProperties);
    }

    @Test
    public void testContainerActionInputSchemaProperties() {
        final var configRoot = schemas.get("action-types_container-action_input");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("ca-input"), actualProperties);
    }

    @Test
    public void testContainerActionOutputSchemaProperties() {
        final var configRoot = schemas.get("action-types_container-action_output");
        assertNotNull(configRoot);
        final var actualProperties = configRoot.properties().keySet();
        assertEquals(Set.of("ca-output"), actualProperties);
    }

    private static void verifyOperationHaveCorrectXmlReference(final Schema schema) {
        final var refValue = schema.ref();
        // In case of a POST RPC with a direct input body and no reference value
        if (refValue != null) {
            final var schemaElement = refValue.substring(refValue.lastIndexOf("/") + 1);
            assertTrue("Reference [" + refValue + "] not found in EXPECTED Schemas",
                EXPECTED_SCHEMAS.contains(schemaElement));
        } else {
            final var type = schema.type();
            assertNotNull(type);
            assertEquals("object", type);
        }
    }

    private static void verifyOperationHaveCorrectJsonReference(final Schema schema) {
        final var properties = schema.properties();
        final String refValue;
        if (properties != null) {
            final var node = properties.values().iterator().next();
            final var type = node.type();
            if (type == null) {
                refValue = node.ref();
            } else if (type.equals("array")) {
                refValue = node.items().ref();
            } else {
                assertEquals("object", type);
                return;
            }
        } else {
            refValue = schema.ref();
        }
        final var schemaElement = refValue.substring(refValue.lastIndexOf("/") + 1);
        assertTrue("Reference [" + refValue + "] not found in EXPECTED Schemas",
                EXPECTED_SCHEMAS.contains(schemaElement));
    }
}

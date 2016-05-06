/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.Maps;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.broker.impl.mount.DOMMountPointServiceImpl;
import org.opendaylight.controller.md.sal.dom.broker.spi.mount.SimpleDOMMountPoint;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

class RestconfModulesServiceTestUtils {
    private static final String MOUNT_POINT = "/mount-point-1:cont/" + RestconfConstants.MOUNT + "/";

    protected static final String TEST_MODULE = "module1/2014-01-01";
    protected static final String NOT_EXISTING_MODULE = "not-existing/2016-01-01";

    protected static final String TEST_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + "module1-behind-mount-point/2014-02-03";
    protected static final String NOT_EXISTING_MODULE_BEHIND_MOUNT_POINT = MOUNT_POINT + NOT_EXISTING_MODULE;

    protected static final List<String> ALLOWED_KEYWORDS = Arrays.asList("name", "revision", "namespace", "feature");

    private static final String modulesPath = "/modules";
    private static final String modulesBehindMountPointPath = "/modules/modules-behind-mount-point";

    private RestconfModulesServiceTestUtils() {}

    protected static Set<Module> getExpectedModules() throws Exception {
        return TestRestconfUtils.loadSchemaContext(RestconfModulesServiceTestUtils.modulesPath).getModules();
    }

    protected static Set<Module> getExpectedModulesBehindMountPoint() throws Exception {
        return TestRestconfUtils.loadSchemaContext(
                RestconfModulesServiceTestUtils.modulesBehindMountPointPath).getModules();
    }

    /**
     * Verify if correct modules were loaded into Restconf module by comparison with modules from <code>SchemaContext</code>
     * @param expectedModules Modules from <code>SchemaContext</code>
     * @param loadedModules Loaded modules into Restconf module
     */
    protected static void verifyModules(final Set<Module> expectedModules, final Set<TestModule> loadedModules) {
        assertEquals("Number of loaded modules is not as expected", expectedModules.size(), loadedModules.size());
        expectedModules.forEach((x) -> loadedModules.remove(
                new TestModule(x.getName(), x.getNamespace(), x.getRevision())));
        assertTrue("All modules should be verified", loadedModules.isEmpty());
    }

    protected static void verifyLoadedModule(final String expectedName, final String expectedRevision,
                                    final String expectedNamespace, final Set<Object> expectedFeatures,
                                    final Iterator loadedModuleEntries) {
        while (loadedModuleEntries.hasNext()) {
            final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) loadedModuleEntries.next());
            final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

            assertTrue("Not allowed keyword", ALLOWED_KEYWORDS.contains(key));

            switch (key) {
                case "name":
                    assertEquals("Not correct module was found",
                            expectedName, ((LeafNode) e.getValue()).getValue());
                    break;
                case "revision":
                    assertEquals("Not correct module was found",
                            expectedRevision, ((LeafNode) e.getValue()).getValue());
                    break;
                case "namespace":
                    assertEquals("Not correct module was found",
                            expectedNamespace, ((LeafNode) e.getValue()).getValue());
                    break;
                case "feature":
                    assertEquals("Not correct module was found",
                            expectedFeatures, ((LeafSetNode) e.getValue()).getValue());
                    break;
            }
        }
    }

    /**
     *
     * @param restconfModulePath
     * @throws Exception
     */
    protected static RestconfModulesServiceImpl setupCustomRestconfModule(
            final String restconfModulePath) throws Exception {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.parseYangSource(restconfModulePath));
        return new RestconfModulesServiceImpl(schemaContextHandler, null);
    }

    /**
     * Get {}
     * @param restconfModulePath
     * @throws Exception
     */
    protected static final RestconfModulesServiceImpl setupCustomRestconfModuleMountPoint(
            final String restconfModulePath)throws Exception {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.parseYangSource(restconfModulePath));

        final DOMMountPointServiceHandler mountPointServiceHandler = mock(DOMMountPointServiceHandler.class);
        when(mountPointServiceHandler.getDOMMountPointService()).thenReturn(getMountPointService());

        return new RestconfModulesServiceImpl(schemaContextHandler, mountPointServiceHandler);
    }

    protected static final RestconfModulesServiceImpl setupNormal() throws Exception {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext
                (RestconfModulesServiceTestUtils.modulesPath));
        return new RestconfModulesServiceImpl(schemaContextHandler, null);
    }

    protected static final RestconfModulesServiceImpl setupNormalMountPoint() throws Exception {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext(
                RestconfModulesServiceTestUtils.modulesPath));

        final DOMMountPointServiceHandler mountPointServiceHandler = mock(DOMMountPointServiceHandler.class);
        when(mountPointServiceHandler.getDOMMountPointService()).thenReturn(getMountPointService());

        return new RestconfModulesServiceImpl(schemaContextHandler, mountPointServiceHandler);
    }

    protected static final RestconfModulesServiceImpl setupMissingRestconfModule() {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        final SchemaContext schemaContext = mock(SchemaContext.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(schemaContext);
        when(schemaContext.findModuleByNamespaceAndRevision(Draft11.RestconfModule.IETF_RESTCONF_QNAME.getNamespace(),
                Draft11.RestconfModule.IETF_RESTCONF_QNAME.getRevision())).thenReturn(null);

        return new RestconfModulesServiceImpl(schemaContextHandler, null);
    }

    protected static final RestconfModulesServiceImpl setupMissingRestconfModuleMountPoint() throws Exception {
        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        final SchemaContext schemaContext = mock(SchemaContext.class);
        when(schemaContextHandler.getSchemaContext()).thenReturn(schemaContext);
        when(schemaContext.findModuleByNamespaceAndRevision(Draft11.RestconfModule.IETF_RESTCONF_QNAME.getNamespace(),
                Draft11.RestconfModule.IETF_RESTCONF_QNAME.getRevision())).thenReturn(null);

        final DOMMountPointServiceHandler mountPointServiceHandler = mock(DOMMountPointServiceHandler.class);
        when(mountPointServiceHandler.getDOMMountPointService()).thenReturn(getMountPointService());

        return new RestconfModulesServiceImpl(schemaContextHandler, mountPointServiceHandler);
    }

    private static final DOMMountPointService getMountPointService() throws Exception {
        final DOMMountPointService mountPointService = new DOMMountPointServiceImpl();
        ((DOMMountPointServiceImpl) mountPointService).registerMountPoint(
                SimpleDOMMountPoint.create(
                        YangInstanceIdentifier.builder().node(
                                QName.create("mount:point:1", "2016-01-01", "cont")).build(),
                        ImmutableClassToInstanceMap.copyOf(Maps.newHashMap()),
                        TestRestconfUtils.loadSchemaContext(
                                RestconfModulesServiceTestUtils.modulesBehindMountPointPath)));

        return mountPointService;
    }

    protected static class TestModule {
        private String name;
        private String namespace;
        private String revision;

        protected TestModule() {}

        public TestModule(String name, URI namespace, Date revision) {
            this.name = name;
            this.namespace = namespace.toString();
            this.revision = SimpleDateFormatUtil.getRevisionFormat().format(revision);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getRevision() {
            return revision;
        }

        public void setRevision(String revision) {
            this.revision = revision;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestModule that = (TestModule) o;

            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null) return false;
            return revision != null ? revision.equals(that.revision) : that.revision == null;

        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
            result = 31 * result + (revision != null ? revision.hashCode() : 0);
            return result;
        }
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.RestConnectorProvider;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractIdentifierAwareJaxRsProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public abstract class AbstractBodyReaderTest {

    protected static final DOMMountPointServiceHandler MOUNT_POINT_SERVICE_HANDLER =
            mock(DOMMountPointServiceHandler.class);

    protected final MediaType mediaType;
    protected final SchemaContextHandler schemaContextHandler;

    protected AbstractBodyReaderTest(SchemaContext schemaContext) throws NoSuchFieldException, IllegalAccessException {
        mediaType = getMediaType();

        final Field mountPointServiceHandlerField =
                RestConnectorProvider.class.getDeclaredField("mountPointServiceHandler");
        mountPointServiceHandlerField.setAccessible(true);
        mountPointServiceHandlerField.set(RestConnectorProvider.class, MOUNT_POINT_SERVICE_HANDLER);

        schemaContextHandler = TestUtils.newSchemaContextHandler(schemaContext);
    }

    protected abstract MediaType getMediaType();

    protected static SchemaContext schemaContextLoader(final String yangPath,
            final SchemaContext schemaContext) {
        return TestRestconfUtils.loadSchemaContext(yangPath, schemaContext);
    }

    protected static <T extends AbstractIdentifierAwareJaxRsProvider> void mockBodyReader(
            final String identifier, final T normalizedNodeProvider,
            final boolean isPost) throws NoSuchFieldException,
            SecurityException, IllegalArgumentException, IllegalAccessException {
        final UriInfo uriInfoMock = mock(UriInfo.class);
        final MultivaluedMap<String, String> pathParm = new MultivaluedHashMap<>(1);

        if (!identifier.isEmpty()) {
            pathParm.put(RestconfConstants.IDENTIFIER, Collections.singletonList(identifier));
        }

        when(uriInfoMock.getPathParameters()).thenReturn(pathParm);
        when(uriInfoMock.getPathParameters(false)).thenReturn(pathParm);
        when(uriInfoMock.getPathParameters(true)).thenReturn(pathParm);
        normalizedNodeProvider.setUriInfo(uriInfoMock);

        final Request request = mock(Request.class);
        if (isPost) {
            when(request.getMethod()).thenReturn("POST");
        } else {
            when(request.getMethod()).thenReturn("PUT");
        }

        normalizedNodeProvider.setRequest(request);
    }

    protected static void checkMountPointNormalizedNodeContext(
            final NormalizedNodeContext nnContext) {
        checkNormalizedNodeContext(nnContext);
        assertNotNull(nnContext.getInstanceIdentifierContext().getMountPoint());
    }

    protected static void checkNormalizedNodeContext(
            final NormalizedNodeContext nnContext) {
        assertNotNull(nnContext.getData());
        assertNotNull(nnContext.getInstanceIdentifierContext()
                .getInstanceIdentifier());
        assertNotNull(nnContext.getInstanceIdentifierContext()
                .getSchemaContext());
        assertNotNull(nnContext.getInstanceIdentifierContext().getSchemaNode());
    }

    protected static void checkPatchContext(final PatchContext patchContext) {
        assertNotNull(patchContext.getData());
        assertNotNull(patchContext.getInstanceIdentifierContext().getInstanceIdentifier());
        assertNotNull(patchContext.getInstanceIdentifierContext().getSchemaContext());
        assertNotNull(patchContext.getInstanceIdentifierContext().getSchemaNode());
    }

    protected static void checkPatchContextMountPoint(final PatchContext patchContext) {
        checkPatchContext(patchContext);
        assertNotNull(patchContext.getInstanceIdentifierContext().getMountPoint());
        assertNotNull(patchContext.getInstanceIdentifierContext().getMountPoint().getSchemaContext());
    }
}

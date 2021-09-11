/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.rest.impl.XmlToPatchBodyReader;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class TestXmlPatchBodyReaderMountPoint extends AbstractBodyReaderTest {

    private final XmlToPatchBodyReader xmlToPatchBodyReader;
    private static EffectiveModelContext schemaContext;
    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount";

    public TestXmlPatchBodyReaderMountPoint() {
        super(schemaContext, mock(DOMMountPoint.class));
        xmlToPatchBodyReader = new XmlToPatchBodyReader(controllerContext);
    }

    @BeforeClass
    public static void initialization() throws NoSuchFieldException, SecurityException {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdata.xml");
        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    public void moduleDataValueMissingNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueMissing.xml");
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).tag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    public void moduleDataNotValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataValueNotSupported.xml");
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).tag());
    }

    /**
     * Test of Yang Patch with absolute target path.
     */
    @Test
    public void moduleDataAbsoluteTargetPathTest() throws Exception {
        final String uri = MOUNT_POINT;
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataAbsoluteTargetPath.xml");
        final PatchContext returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataCompleteTargetInURI.xml");
        final PatchContext returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void moduleDataMergeOperationOnListTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnList.xml");
        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void moduleDataMergeOperationOnContainerTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnContainer.xml");
        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }
}

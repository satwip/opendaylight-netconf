/*
 * Copyright (c) 2014, 2015 Brocade Communication Systems, Inc., Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class CodecsExceptionsCatchingTest extends JerseyTest {

    private static RestconfImpl restConf;
    private static ControllerContext controllerContext = ControllerContext.getInstance();

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        restConf = RestconfImpl.getInstance();
        controllerContext = ControllerContext.getInstance();
        final SchemaContext schemaContext = TestUtils.loadSchemaContext("/decoding-exception/yang");
        controllerContext.setGlobalSchema(schemaContext);
        restConf.setControllerContext(controllerContext);
    }

    @Override
    protected Application configure() {
        /* enable/disable Jersey logs to console */
        // enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        // enable(TestProperties.RECORD_LOG_LEVEL);
        // set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restConf, new NormalizedNodeJsonBodyWriter(),
            new NormalizedNodeXmlBodyWriter(), new XmlNormalizedNodeBodyReader(), new JsonNormalizedNodeBodyReader());
        resourceConfig.registerClasses(RestconfDocumentedExceptionMapper.class);
        return resourceConfig;
    }

    @Test
    @Ignore // TODO RestconfDocumentedExceptionMapper needs be fixed before
    public void stringToNumberConversionError() {
        final Response response = target("/config/number:cont").request(MediaType.APPLICATION_XML).put(
                Entity.entity("<cont xmlns=\"number\"><lf>3f</lf></cont>", MediaType.APPLICATION_XML));
        final String exceptionMessage = response.readEntity(String.class);
        assertTrue(exceptionMessage.contains("invalid-value"));
    }
}
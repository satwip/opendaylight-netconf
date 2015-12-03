/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.rest.api.Draft02.MediaTypes;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;

@Provider
@Produces({MediaTypes.PATCH_STATUS + RestconfService.JSON})
public class PATCHJsonBodyWriter implements MessageBodyWriter<PATCHStatusContext> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(PATCHStatusContext.class);
    }

    @Override
    public long getSize(PATCHStatusContext patchStatusContext, Class<?> type, Type genericType, Annotation[]
            annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(PATCHStatusContext patchStatusContext, Class<?> type, Type genericType, Annotation[]
            annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                throws IOException, WebApplicationException {
        System.out.println("patchId: " + patchStatusContext.getPatchId());

        //TODO implement, existing json capabilities can be used, because there is no anyxml in response
    }
}

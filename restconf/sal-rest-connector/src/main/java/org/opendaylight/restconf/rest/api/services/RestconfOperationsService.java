/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.api.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.Draft09;
import org.opendaylight.restconf.utils.RestconfConstants;

/**
 * Container that provides access to the data-model specific operations
 * supported by the server.
 *
 */
public interface RestconfOperationsService {

    /**
     * List of rpc or action operations supported by the server.
     *
     * @param uriInfo
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("/operations")
    @Produces({ Draft09.MediaTypes.API + RestconfConstants.JSON, Draft09.MediaTypes.API + RestconfConstants.XML,
            MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getOperations(@Context UriInfo uriInfo);

    /**
     * Valid for mount points. List of operations supported by the server.
     *
     * @param identifier
     * @param uriInfo
     * @return {@link NormalizedNodeContext}
     */
    @GET
    @Path("/operations/{identifier:.+}")
    @Produces({ Draft09.MediaTypes.API + RestconfConstants.JSON, Draft09.MediaTypes.API + RestconfConstants.XML,
            MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getOperations(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);
}
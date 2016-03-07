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
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.utils.RestconfConstants;

@Path("/data/ietf-yang-library:modules")
public interface RestconfModulesService {

    @GET
    @Path("")
    @Produces({ Draft09.MediaTypes.API + RestconfConstants.JSON, Draft09.MediaTypes.API + RestconfConstants.XML,
            MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModules(@Context UriInfo uriInfo);

    @GET
    @Path("/{identifier:.+}")
    @Produces({ Draft09.MediaTypes.API + RestconfConstants.JSON, Draft09.MediaTypes.API + RestconfConstants.XML,
            MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModules(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);

    @GET
    @Path("/module/{identifier:.+}")
    @Produces({ Draft09.MediaTypes.API + RestconfConstants.JSON, Draft09.MediaTypes.API + RestconfConstants.XML,
            MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public NormalizedNodeContext getModule(@PathParam("identifier") String identifier, @Context UriInfo uriInfo);
}
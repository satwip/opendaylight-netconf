/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.api;

import com.google.common.collect.ImmutableCollection;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * This service generates swagger (See <a
 * href="https://helloreverb.com/developers/swagger"
 * >https://helloreverb.com/developers/swagger</a>) compliant documentation for
 * RESTCONF APIs. The output of this is used by embedded Swagger UI.
 */
@Path("/")
public interface OpenApiService {

    /**
     * Generate OpenAPI specification document.
     *
     * <p>
     * Generate OpenAPI specification document for modules of controller schema context starting with a module with
     * index specified by {@code offset}. The resulting documentation contains as many modules as defined
     * by {@code limit}.
     *
     * <p>
     * If user wishes to get document for all modules in controller's model context then value 0 should be used
     * for both {@code offset} and {@code limit}.
     *
     * <p>
     * We relly on {@link EffectiveModelContext} usage of {@link ImmutableCollection} which preserves iteration order,
     * so we are able to read first 40 modules with {@code ?offset=0&limit=20} and consequent request with parameters
     * {@code ?offset=20&limit=20}.
     *
     * <p>
     * If user uses value out of range for {@code offset} which is greater than number of modules in mount point schema
     * or negative value, the response will contain empty OpenAPI document. Same if user uses negative value for
     * {@code limit}.
     *
     * @param uriInfo Requests {@link UriInfo}.
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return Response containing the OpenAPI document for number of modules specified by {@code offset}
     *     and {@code limit}.
     * @throws IOException When I/O error occurs.
     */
    @GET
    @Path("/single")
    @Produces(MediaType.APPLICATION_JSON)
    Response getAllModulesDoc(@Context UriInfo uriInfo, @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit) throws IOException;

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @GET
    @Path("/{module}({revision})")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDocByModule(@PathParam("module") String module, @PathParam("revision") String revision,
                            @Context UriInfo uriInfo) throws IOException;

    /**
     * Redirects to embedded swagger ui.
     */
    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    Response getApiExplorer(@Context UriInfo uriInfo);

    /**
     * Generates index document for Swagger UI. This document lists out all
     * modules with link to get APIs for each module. The API for each module is
     * served by <code> getDocByModule()</code> method.
     */
    @GET
    @Path("/mounts")
    @Produces(MediaType.APPLICATION_JSON)
    Response getListOfMounts(@Context UriInfo uriInfo);

    /**
     * Generates Swagger compliant document listing APIs for module.
     */
    @GET
    @Path("/mounts/{instance}/{module}({revision})")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDocByModule(@PathParam("instance") String instanceNum,
                                 @PathParam("module") String module, @PathParam("revision") String revision,
                                 @Context UriInfo uriInfo) throws IOException;

    /**
     * Generate OpenAPI specification document.
     *
     * <p>
     * Generates OpenAPI specification document listing APIs for all modules if value 0 is used for both {@code offset}
     * and {@code limit}.
     *
     * <p>
     * Generate OpenAPI specification document for modules of mount point schema context starting with a module with
     * index specified by {@code offset}. The resulting documentation contains as many modules as defined
     * by {@code limit}.
     *
     * <p>
     * We relly on {@link EffectiveModelContext} usage of {@link ImmutableCollection} which preserves iteration order,
     * so we are able to read first 40 modules with {@code ?offset=0&limit=20} and consequent request with parameters
     * {@code ?offset=20&limit=20}.
     *
     * <p>
     * If user uses value out of range for {@code offset} which is greater than number of modules in mount point schema
     * or negative value, the response will contain empty OpenAPI document. Same if user uses negative value for
     * {@code limit}.
     *
     * @param instanceNum Instance number of the mount point.
     * @param uriInfo Requests {@link UriInfo}.
     * @param offset First model to read. 0 means read from the first model.
     * @param limit The number of models to read. 0 means read all models.
     * @return Response containing the OpenAPI document for number of modules specified by {@code offset}
     *     and {@code limit}.
     * @throws IOException When I/O error occurs.
     */
    @GET
    @Path("/mounts/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMountDoc(@PathParam("instance") String instanceNum, @Context UriInfo uriInfo,
            @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) throws IOException;
}

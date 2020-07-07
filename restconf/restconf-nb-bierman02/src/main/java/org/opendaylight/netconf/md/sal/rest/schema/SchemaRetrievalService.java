/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.md.sal.rest.schema;

import com.google.common.annotations.Beta;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.yangtools.yang.common.YangConstants;

/**
 * Retrieval of the YANG modules which server supports.
 *
 * @deprecated do not use this api. It is replaced by RestconfSchemaService
 */
@Deprecated
@Beta
public interface SchemaRetrievalService {
    @GET
    @Produces({YangConstants.RFC6020_YIN_MEDIA_TYPE, YangConstants.RFC6020_YANG_MEDIA_TYPE})
    @Path("/modules/module/{identifier:.+}/schema")
    SchemaExportContext getSchema(@PathParam("identifier") String mountAndModuleId);
}

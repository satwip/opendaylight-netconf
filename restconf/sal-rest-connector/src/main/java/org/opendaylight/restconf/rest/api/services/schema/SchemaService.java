/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.api.services.schema;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.restconf.utils.RestconfConstants;

public interface SchemaService {

    @GET
    @Produces({ RestconfConstants.YIN_MEDIA_TYPE, RestconfConstants.YANG_MEDIA_TYPE })
    @Path("/ietf-yang-library:modules/module/{identifier:.+}/schema")
    SchemaExportContext getSchema(@PathParam("identifier") String mountAndModuleId);
}

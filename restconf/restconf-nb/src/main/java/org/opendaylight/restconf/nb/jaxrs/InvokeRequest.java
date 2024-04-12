/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerException;

@NonNullByDefault
final class InvokeRequest extends JaxRsServerRequest<InvokeResult> {
    InvokeRequest(final PrettyPrintParam defaultPrettyPrint, final UriInfo uriInfo, final AsyncResponse ar) {
        super(defaultPrettyPrint, uriInfo, ar);
    }

    @Override
    Response createResponse(final PrettyPrintParam prettyPrint, final InvokeResult result) throws ServerException {
        final var body = result.output();
        return body == null ? Response.noContent().build()
            : Response.ok().entity(new JaxRsFormattableBody(body, prettyPrint)).build();
    }
}

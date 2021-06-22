/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = ApiDocApplication.class)
public class ApiDocApplication extends Application {
    private final ApiDocService apiDocService;

    @Inject
    @Activate
    public ApiDocApplication(final @Reference ApiDocService apiDocService) {
        this.apiDocService = requireNonNull(apiDocService);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(apiDocService, new JaxbContextResolver(), new JacksonJaxbJsonProvider());
    }
}

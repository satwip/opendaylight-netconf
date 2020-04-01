/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * Base class for an RFC 8040 implementation.
 *
 * @author Thomas Pantelis
 */
public abstract class BaseYangSwaggerGeneratorRFC8040 extends BaseYangSwaggerGenerator {

    private static final String DEFAULT_BASE_PATH = "rests";
    private static final String PATH_VERSION = "rfc8040";
    private final String basePath;

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService) {
        super(schemaService);
        this.basePath = DEFAULT_BASE_PATH;
    }

    protected BaseYangSwaggerGeneratorRFC8040(final Optional<DOMSchemaService> schemaService, final String basePath) {
        super(schemaService);
        this.basePath = basePath;
    }

    @Override
    protected String getPathVersion() {
        return PATH_VERSION;
    }

    @Override
    public String getResourcePath(final String resourceType, final String context) {
        if (isData(resourceType)) {
            return "/" + basePath + "/data" + context;
        }
        return "/" + basePath + "/operations" + context;
    }

    @Override
    public String getResourcePathPart(String resourceType) {
        if (isData(resourceType)) {
            return "data";
        }
        return "operations";
    }

    private boolean isData(String dataStore) {
        return "config".contains(dataStore) || "operational".contains(dataStore);
    }

    @Override
    protected ListPathBuilder newListPathBuilder() {
        return new ListPathBuilder() {
            private String prefix = "=";

            @Override
            public String nextParamIdentifier(final String key) {
                final String str = prefix + "{" + key + "}";
                prefix = ",";
                return str;
            }
        };
    }

    @Override
    protected void appendPathKeyValue(final StringBuilder builder, final Object value) {
        builder.deleteCharAt(builder.length() - 1).append("=").append(value).append('/');
    }
}

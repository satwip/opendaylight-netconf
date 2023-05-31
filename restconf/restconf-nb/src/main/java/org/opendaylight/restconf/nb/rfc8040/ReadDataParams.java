/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore read operations.
 */
@Beta
public record ReadDataParams(ContentParam content, DepthParam depth, FieldsParam fields, WithDefaultsParam withDefaults,
                             boolean tagged, PrettyPrintParam prettyPrint) {
    private static final @NonNull ReadDataParams EMPTY =
        new ReadDataParams(ContentParam.ALL, null, null, null, false, null);

    public ReadDataParams {
        requireNonNull(content);
    }

    public static @NonNull ReadDataParams empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).add("content", content.paramValue());
        if (depth != null) {
            helper.add("depth", depth.value());
        }
        if (fields != null) {
            helper.add("fields", fields.toString());
        }
        if (withDefaults != null) {
            helper.add("withDefaults", withDefaults.paramValue());
        }
        helper.add("tagged", tagged);
        if (prettyPrint != null) {
            helper.add("prettyPrint", prettyPrint.value());
        }
        return helper.toString();
    }
}

/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;

/**
 * Supported query parameters of {@code /data} {@code GET} HTTP operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.3">RFC8040 section 4.3</a>.
 */
public record DataGetParams(
        @NonNull ContentParam content,
        @Nullable DepthParam depth,
        @Nullable FieldsParam fields,
        @Nullable WithDefaultsParam withDefaults,
        @NonNull PrettyPrintParam prettyPrint) implements FormatParameters {
    public static final @NonNull DataGetParams EMPTY =
        new DataGetParams(ContentParam.ALL, null, null, null, PrettyPrintParam.FALSE);

    public DataGetParams {
        requireNonNull(content);
        requireNonNull(prettyPrint);
    }

    /**
     * Return {@link DataGetParams} for specified query parameters.
     *
     * @param params Parameters and their values
     * @return A {@link DataGetParams}
     * @throws NullPointerException if {@code queryParameters} is {@code null}
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public static @NonNull DataGetParams of(final QueryParams params) {
        ContentParam content = ContentParam.ALL;
        DepthParam depth = null;
        FieldsParam fields = null;
        WithDefaultsParam withDefaults = null;
        PrettyPrintParam prettyPrint = params.prettyPrint();

        for (var entry : params.asCollection()) {
            final var name = entry.getKey();
            final var value = entry.getValue();

            switch (name) {
                case ContentParam.uriName:
                    content = parseParam(ContentParam::forUriValue, name, value);
                    break;
                case DepthParam.uriName:
                    depth = DepthParam.forUriValue(value);
                    break;
                case FieldsParam.uriName:
                    fields = parseParam(FieldsParam::forUriValue, name, value);
                    break;
                case WithDefaultsParam.uriName:
                    withDefaults = parseParam(WithDefaultsParam::forUriValue, name, value);
                    break;
                case PrettyPrintParam.uriName:
                    prettyPrint = parseParam(PrettyPrintParam::forUriValue, name, value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown parameter in /data GET: " + name);
            }
        }

        return new DataGetParams(content, depth, fields, withDefaults, prettyPrint);
    }

    private static <T> @NonNull T parseParam(final Function<@NonNull String, @NonNull T> method, final String name,
            final @NonNull String value) {
        try {
            return method.apply(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + name + " value: " + e.getMessage(), e);
        }
    }
}

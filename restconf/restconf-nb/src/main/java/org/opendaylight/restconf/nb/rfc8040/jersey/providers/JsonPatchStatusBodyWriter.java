/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.spi.FormattableBodySupport;

@Provider
@Produces(MediaTypes.APPLICATION_YANG_DATA_JSON)
public class JsonPatchStatusBodyWriter extends AbstractPatchStatusBodyWriter {
    @Override
    public void writeTo(final PatchStatusContext patchStatusContext, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        final var jsonWriter = FormattableBodySupport.createJsonWriter(entityStream, () -> PrettyPrintParam.FALSE);
        jsonWriter.beginObject().name("ietf-yang-patch:yang-patch-status")
            .beginObject().name("patch-id").value(patchStatusContext.patchId());

        if (patchStatusContext.ok()) {
            reportSuccess(jsonWriter);
        } else {
            final var globalErrors = patchStatusContext.globalErrors();
            if (globalErrors != null) {
                reportErrors(patchStatusContext.databind(), globalErrors, jsonWriter);
            } else {
                jsonWriter.name("edit-status").beginObject()
                    .name("edit").beginArray();
                for (var editStatus : patchStatusContext.editCollection()) {
                    jsonWriter.beginObject().name("edit-id").value(editStatus.getEditId());

                    final var editErrors = editStatus.getEditErrors();
                    if (editErrors != null) {
                        reportErrors(patchStatusContext.databind(), editErrors, jsonWriter);
                    } else if (editStatus.isOk()) {
                        reportSuccess(jsonWriter);
                    }
                    jsonWriter.endObject();
                }
                jsonWriter.endArray().endObject();
            }
        }
        jsonWriter.endObject().endObject().flush();
    }

    private static void reportSuccess(final JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("ok").beginArray().nullValue().endArray();
    }

    private static void reportErrors(final DatabindContext databind, final List<RestconfError> errors,
            final JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("errors").beginObject().name("error").beginArray();

        for (var restconfError : errors) {
            jsonWriter.beginObject()
                .name("error-type").value(restconfError.getErrorType().elementBody())
                .name("error-tag").value(restconfError.getErrorTag().elementBody());

            final var errorPath = restconfError.getErrorPath();
            if (errorPath != null) {
                jsonWriter.name("error-path");
                databind.jsonCodecs().instanceIdentifierCodec().writeValue(jsonWriter, errorPath);
            }
            final var errorMessage = restconfError.getErrorMessage();
            if (errorMessage != null) {
                jsonWriter.name("error-message").value(errorMessage);
            }
            final var errorInfo = restconfError.getErrorInfo();
            if (errorInfo != null) {
                jsonWriter.name("error-info").value(errorInfo);
            }

            jsonWriter.endObject();
        }

        jsonWriter.endArray().endObject();
    }
}

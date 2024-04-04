/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.ReplyBody;

abstract sealed class ReplyBodyWriter implements MessageBodyWriter<ReplyBody>
        permits JsonReplyBodyWriter, XmlReplyBodyWriter {
    @Override
    public final boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return ReplyBody.class.isAssignableFrom(type);
    }

    @Override
    public final void writeTo(final ReplyBody t, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        writeTo(requireNonNull(t), requireNonNull(entityStream));
    }

    abstract void writeTo(@NonNull ReplyBody body, @NonNull OutputStream out) throws IOException;
}

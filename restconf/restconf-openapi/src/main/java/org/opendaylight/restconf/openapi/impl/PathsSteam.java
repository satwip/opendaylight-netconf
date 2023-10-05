/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.PathEntity;
import org.opendaylight.restconf.openapi.model.PostEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class PathsSteam extends InputStream {
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;
    private boolean eof;

    public PathsSteam(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream) {
        this.generator = generator;
        this.stream = stream;

        for (final var module : context.getModules()) {
            stack.add(new PathStream(toPaths(module), writer));
        }
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("paths");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()));
                stream.reset();
                eof = true;
                return reader.read();
            }
            reader = new InputStreamReader(stack.pop());
            read = reader.read();
        }

        return read;
    }

    private static Deque<PathEntity> toPaths(final Module module) {
        final var result = new ArrayDeque<PathEntity>();
        // RPC operations (via post) - RPCs have their own path
        for (final var rpc : module.getRpcs()) {
            // TODO connect path with payload
            final var post = new PostEntity(rpc, "controller", module.getName());
            final String resolvedPath = "rests/operations/" + "/" + module.getName() + ":"
                + rpc.getQName().getLocalName();
            final var entity = new PathEntity(resolvedPath, post);
            result.add(entity);
        }

        for (final var container : module.getChildNodes()) {
            // get
            // post
            // put
            // patch
            // delete

            // add path into deque
        }
        return result;
    }
}

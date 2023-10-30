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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.restconf.openapi.model.security.Http;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class SchemasStream extends InputStream {
    private static final String BASIC_AUTH_NAME = "basicAuth";
    private static final Http OPEN_API_BASIC_AUTH = new Http("basic", null, null);

    private final Iterator<? extends Module> iterator;
    private final OpenApiBodyWriter writer;
    private final JsonGenerator generator;
    private final ByteArrayOutputStream stream;

    private Reader reader;
    private boolean schemesWritten;
    private boolean eof;
    private boolean eos;

    public SchemasStream(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream) {
        iterator = context.getModules().iterator();
        this.writer = writer;
        this.generator = generator;
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            generator.writeObjectFieldStart("components");
            generator.writeObjectFieldStart("schemas");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
        }
        if (eof) {
            return -1;
        }
        if (eos) {
            eof = true;
            return reader.read();
        }

        var read = reader.read();
        while (read == -1) {
            if (iterator.hasNext()) {
                reader = new InputStreamReader(new SchemaStream(toComponents(iterator.next()), writer),
                    StandardCharsets.UTF_8);
                read = reader.read();
                continue;
            }
            if (!schemesWritten) {
                reader = new InputStreamReader(new SecuritySchemasStream(writer, Map.of(BASIC_AUTH_NAME,
                    OPEN_API_BASIC_AUTH)), StandardCharsets.UTF_8);
                read = reader.read();
                schemesWritten = true;
                continue;
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
            eos = true;
            return reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }

    private static Deque<SchemaEntity> toComponents(final Module module) {
        final var result = new ArrayDeque<SchemaEntity>();
        for (final var rpc : module.getRpcs()) {
            final var moduleName = module.getName();
            final var rpcName = rpc.getQName().getLocalName();
            final var input = new SchemaEntity(rpc.getInput(), moduleName + "_" + rpcName + "_input", "object");
            result.add(input);
            final var output = new SchemaEntity(rpc.getOutput(), moduleName + "_" + rpcName + "_output", "object");
            result.add(output);
        }

        // actions
        // child nodes
        // etc.
        return result;
    }
}

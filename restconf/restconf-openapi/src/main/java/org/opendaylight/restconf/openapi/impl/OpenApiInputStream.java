/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
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
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiVersionEntity;
import org.opendaylight.restconf.openapi.model.SecurityEntity;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.restconf.openapi.model.ServersEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class OpenApiInputStream extends InputStream {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(stream);
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;

    private boolean eof;

    public OpenApiInputStream(final EffectiveModelContext context, final String openApiVersion, final Info info,
            final List<Server> servers, final List<Map<String, List<String>>> security) throws IOException {
        final OpenApiBodyWriter writer = new OpenApiBodyWriter(generator, stream);
        stack.add(new OpenApiVersionStream(new OpenApiVersionEntity(), writer));
        stack.add(new InfoStream(new InfoEntity(info.version(), info.title(), info.description()), writer));
        stack.add(new ServersStream(new ServersEntity(
            List.of(new ServerEntity(servers.iterator().next().url()))), writer));
        stack.add(new PathsSteam(context, writer, generator, stream));
        stack.add(new SchemasStream(context, writer, generator, stream));
        stack.add(new SecurityStream(writer, new SecurityEntity(security)));
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeStartObject();
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
                stream.reset();
                eof = true;
                return reader.read();
            }
            reader = new InputStreamReader(stack.pop(), StandardCharsets.UTF_8);
            read = reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }
}

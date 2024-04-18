/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiVersionEntity;
import org.opendaylight.restconf.openapi.model.SecurityEntity;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.restconf.openapi.model.ServersEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class OpenApiInputStream extends InputStream {
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final Deque<InputStream> stack = new ArrayDeque<>();

    private Reader reader;
    private ReadableByteChannel channel;

    private boolean eof;

    public OpenApiInputStream(final EffectiveModelContext context, final String title, final String url,
            final List<Map<String, List<String>>> security, final String deviceName, final String urlPrefix,
            final boolean isForSingleModule, final boolean includeDataStore, final Collection<? extends Module> modules,
            final String basePath, final OpenApiBodyWriter writer, final ByteArrayOutputStream stream,
            final JsonGenerator generator) {
        this.generator = generator;
        this.stream = stream;
        stack.add(new OpenApiVersionStream(new OpenApiVersionEntity(), writer));
        stack.add(new InfoStream(new InfoEntity(title), writer));
        stack.add(new ServersStream(new ServersEntity(List.of(new ServerEntity(url))), writer));
        stack.add(new PathsStream(context, writer, deviceName, urlPrefix, isForSingleModule, includeDataStore,
            modules.iterator(), basePath, stream, generator));
        stack.add(new ComponentsStream(context, writer, generator, stream, modules.iterator(), isForSingleModule));
        stack.add(new SecurityStream(writer, new SecurityEntity(security)));
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("data");
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
                stream.reset();
                eof = true;
                return reader.read();
            }
            reader = new BufferedReader(new InputStreamReader(stack.pop(), StandardCharsets.UTF_8));
            read = reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (eof) {
            return -1;
        }
        if (channel == null) {
            generator.writeObjectFieldStart("data");
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = channel.read(ByteBuffer.wrap(array, off, len));
        while (read == -1) {
            if (stack.isEmpty()) {
                generator.writeEndObject();
                generator.flush();
                channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
                stream.reset();
                eof = true;
                return channel.read(ByteBuffer.wrap(array, off, len));
            }
            channel = Channels.newChannel(stack.pop());
            read = channel.read(ByteBuffer.wrap(array, off, len));
        }

        return read;
    }
}

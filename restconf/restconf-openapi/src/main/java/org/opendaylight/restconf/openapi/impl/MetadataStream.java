/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;


import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

public final class MetadataStream extends InputStream {
    private final @NonNull Map<String, Object> mappedMetadata;
    private final @NonNull OpenApiBodyWriter writer;

    private Reader reader;
    private ReadableByteChannel channel;

    public MetadataStream(final @NonNull Map<String, Object> mappedMetadata, final @NonNull OpenApiBodyWriter writer) {
        this.writer = requireNonNull(writer);
        this.mappedMetadata = requireNonNull(mappedMetadata);
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
                writeNextEntity(new MetadataEntity(mappedMetadata))), StandardCharsets.UTF_8));
        }
        return reader.read();
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (channel == null) {
            channel =
                Channels.newChannel(new ByteArrayInputStream(writeNextEntity(new MetadataEntity(mappedMetadata))));
        }
        return channel.read(ByteBuffer.wrap(array, off, len));
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, null);
        return writer.readFrom();
    }
}

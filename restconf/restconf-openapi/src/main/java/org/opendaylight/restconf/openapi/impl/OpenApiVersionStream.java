/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.OpenApiVersionEntity;

public final class OpenApiVersionStream extends InputStream {
    private final OpenApiVersionEntity entity;
    private final OpenApiBodyWriter writer;

    private Reader reader;

    public OpenApiVersionStream(final OpenApiVersionEntity entity, final OpenApiBodyWriter writer) {
        this.entity = entity;
        this.writer = writer;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(entity)), StandardCharsets.UTF_8);
        }
        return reader.read();
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }

    private byte[] writeNextEntity(final OpenApiEntity next) throws IOException {
        writer.writeTo(next, null, null, null, null, null, null);
        return writer.readFrom();
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
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
import java.util.Deque;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.PathEntity;

public final class PathStream extends InputStream {
    private final Deque<PathEntity> stack;
    private final OpenApiBodyWriter writer;

    private Reader reader;

    public PathStream(final Deque<PathEntity> paths, final OpenApiBodyWriter writer) {
        this.stack = paths;
        this.writer = writer;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            if (stack.isEmpty()) {
                return -1;
            }
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(stack.pop())));
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                return -1;
            }
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(stack.pop())));
            read = reader.read();
        }

        return read;
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, null);
        return writer.readFrom();
    }
}

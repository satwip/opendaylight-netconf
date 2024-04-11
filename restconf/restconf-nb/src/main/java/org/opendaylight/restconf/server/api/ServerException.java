/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * A server-side processing exception, reporting a single {@link ServerError}. This exception is not serializable on
 * purpose.
 */
@NonNullByDefault
public final class ServerException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 0L;

    private final ServerError error;

    private ServerException(final String message, final ServerError error, final @Nullable Throwable cause) {
        super(message, cause);
        this.error = requireNonNull(error);
    }

    public ServerException(final String message) {
        this(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, requireNonNull(message));
    }

    public ServerException(final Throwable cause) {
        this(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, cause);
    }

    public ServerException(final String message, final @Nullable Throwable cause) {
        this(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, requireNonNull(message));
    }

    public ServerException(final ErrorType type, final ErrorTag tag, final String message) {
        this(type, tag, message, (Throwable) null);
    }

    public ServerException(final ErrorType type, final ErrorTag tag, final Throwable cause) {
        this(cause.toString(), new ServerError(type, tag, null, cause.getMessage(), null, null), cause);
    }

    public ServerException(final ErrorType type, final ErrorTag tag, final String message,
            final @Nullable Throwable cause) {
        this(requireNonNull(message), new ServerError(type, tag, null, message, null, null), cause);
    }

    public ServerException(final ErrorType type, final ErrorTag tag, final String format,
            final @Nullable Object... args) {
        this(type, tag, format.formatted(args));
    }

    public ServerError error() {
        return error;
    }

    @java.io.Serial
    private void readObjectNoData() throws ObjectStreamException {
        throw new NotSerializableException();
    }

    @java.io.Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        throw new NotSerializableException();
    }

    @java.io.Serial
    private void writeObject(final ObjectOutputStream stream) throws IOException {
        throw new NotSerializableException();
    }
}

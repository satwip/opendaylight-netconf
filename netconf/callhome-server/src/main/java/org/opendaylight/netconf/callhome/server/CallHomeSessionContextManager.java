/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Manager service for active Call-Home connections. Serves session context per id mapping (registry).
 *
 * @param <T> class representing transport specific implementation of {@link CallHomeSessionContext}
 */
public interface CallHomeSessionContextManager<T extends CallHomeSessionContext> extends AutoCloseable {

    /**
     * Registers (maps) new context object. Omits any action if context with same id is already registered.
     *
     * @param context context object associated with new incoming connection
     * @return {@code true} if context id is unique and registration succeeded, {@code false} if context with same
     *      id already exists
     */
    boolean register(T context);

    /**
     * Searches for session context associated with requested {@link Channel}.
     *
     * @param channel {@link Channel} instance
     * @return session context instance if found, {@code null} otherwise
     */
    @Nullable T findByChannel(@NonNull Channel channel);

    /**
     * Removes session context by id from registry. Connection associated with removed context will be closed if open.
     *
     * @param id unique session context identifier
     */
    void remove(@NonNull String id);
}

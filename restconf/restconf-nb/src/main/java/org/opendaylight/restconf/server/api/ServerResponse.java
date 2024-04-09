/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * The result of a {@link RestconfServer} request. This can either be a {@link ServerResponse.Success} or a
 * {@link ServerResponse.Failure}.
 */
@NonNullByDefault
public sealed interface ServerResponse permits ServerResponse.Success, ServerResponse.Failure {
    /**
     * A successful {@link ServerResponse}.
     */
    non-sealed interface Success extends ServerResponse {
        // Marker interface
    }

    /**
     * A failed {@link ServerResponse}, composed of an {@link #errorTag()} and a {@link #body()}.
     */
    record Failure(ErrorTag errorTag, FormattableBody body) implements ServerResponse {
        public Failure {
            requireNonNull(errorTag);
            requireNonNull(body);
        }
    }
}

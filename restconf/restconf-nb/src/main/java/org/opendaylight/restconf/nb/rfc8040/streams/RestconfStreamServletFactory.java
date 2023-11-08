/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import javax.servlet.http.HttpServlet;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.spi.RestconfStreamRegistry;

/**
 * A helper for creating {@link HttpServlet}s which provide bridge between JAX-RS and {@link RestconfStreamRegistry}.
 */
public interface RestconfStreamServletFactory {

    @NonNull HttpServlet newStreamServlet();
}

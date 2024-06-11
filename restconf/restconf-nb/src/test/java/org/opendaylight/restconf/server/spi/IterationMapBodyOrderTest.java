/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class IterationMapBodyOrderTest extends MapBodyOrderTest {
    @Test
    void noReorderEvenIfNeeded() {
        final var entry = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(BAR_BAZ_NID)
            .withChild(FOO_LEAF)
            .withChild(BAZ_LEAF)
            .withChild(BAR_LEAF)
            .build();
        assertEquals(entry.body(), IterationMapBodyOrder.INSTANCE.orderBody(entry));
    }
}

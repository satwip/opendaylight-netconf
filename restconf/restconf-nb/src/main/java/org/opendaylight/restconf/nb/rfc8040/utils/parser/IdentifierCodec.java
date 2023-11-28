/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Codec for identifier to serialize {@link YangInstanceIdentifier} to {@link String} and deserialize {@link String} to
 * {@link YangInstanceIdentifier}.
 */
public final class IdentifierCodec {
    private IdentifierCodec() {
        // Hidden on purpose
    }

    public static String serialize(final YangInstanceIdentifier data, final DatabindContext databind) {
        return new YangInstanceIdentifierSerializer(databind).serializePath(data);
    }
}

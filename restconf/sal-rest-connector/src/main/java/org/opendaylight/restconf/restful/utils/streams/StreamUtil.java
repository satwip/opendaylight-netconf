/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils.streams;

/**
 * Common util class for stream
 * <ul>
 * <li>{@link SubscribeToStreamUtil}
 * <li>{@link CreateStreamUtil}
 * </ul>
 *
 */
public final class StreamUtil {

    private StreamUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Resolve specific type of enum by value.
     *
     * @param clazz
     *            - enum
     * @param value
     *            - string of enum
     * @return - enum
     */
    public static <T> T resolveEnum(final Class<T> clazz, final String value) {
        for (final T t : clazz.getEnumConstants()) {
            if (((Enum<?>) t).name().equals(value)) {
                return t;
            }
        }
        return null;
    }
}

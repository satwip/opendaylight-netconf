/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import org.opendaylight.mdsal.dom.api.DOMActionException;

public class ClusteringActionException extends DOMActionException {
    private static final long serialVersionUID = 1L;

    public ClusteringActionException(final String message) {
        super(message);
    }

    public ClusteringActionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
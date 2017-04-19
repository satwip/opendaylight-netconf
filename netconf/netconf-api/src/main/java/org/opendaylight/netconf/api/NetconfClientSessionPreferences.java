/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api;

import org.opendaylight.netconf.api.messages.NetconfHelloMessage;

/**
 * The only input for the start of a NETCONF session is hello-message.
 */
public final class NetconfClientSessionPreferences extends NetconfSessionPreferences {

    private final NetconfMessage startExiMessage;

    public NetconfClientSessionPreferences(final NetconfHelloMessage helloMessage,
                                           final NetconfMessage startExiMessage) {
        super(helloMessage);
        this.startExiMessage = startExiMessage;
    }

    /**
     * Getter for {@code NetconfMessage}.
     *
     * @return the startExiMessage
     */
    public NetconfMessage getStartExiMessage() {
        return startExiMessage;
    }
}

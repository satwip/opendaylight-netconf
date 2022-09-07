/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Base class for TLS TransportStacks.
 */
public abstract sealed class TLSTransportStack extends AbstractTransportStack permits TLSClient, TLSServer {
    private final TCPTransportStack tcpStack;

    TLSTransportStack(final TCPTransportStack tcpStack) {
        this.tcpStack = requireNonNull(tcpStack);
    }

    @Override
    protected final ListenableFuture<Empty> startShutdown() {
        return tcpStack.shutdown();
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls.config;

import static java.util.Objects.requireNonNull;

/**
 * Represents content of ietf-truststore:local-or-truststore-certs-grouping.
 */
record X509CertificateInfo(String name, byte[] bytes) {
    X509CertificateInfo {
        requireNonNull(name);
        requireNonNull(bytes);
    }
}

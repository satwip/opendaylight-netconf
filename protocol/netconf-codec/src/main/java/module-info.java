/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.codec {
    exports org.opendaylight.netconf.codec;

    requires transitive io.netty.buffer;
    requires transitive io.netty.codec;
    requires transitive io.netty.transport;
    requires com.google.common;
    requires org.opendaylight.netconf.api;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.checkerframework.checker.qual;
    requires static transitive org.eclipse.jdt.annotation;
}

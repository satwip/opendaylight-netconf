/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

module org.opendaylight.restconf.server {
    exports org.opendaylight.restconf.server;

    provides YangFeatureProvider with org.opendaylight.restconf.server.impl.IetfRestconfServerFeatureProvider;

    requires transitive org.opendaylight.netconf.transport.api;
//    requires transitive org.opendaylight.netconf.transport.http;
//    requires transitive org.opendaylight.netconf.transport.tcp;
    requires transitive org.opendaylight.restconf.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.common;
    requires org.opendaylight.restconf.server.api;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static javax.inject;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.annotation.bundle;
    requires static org.osgi.service.component.annotations;
}
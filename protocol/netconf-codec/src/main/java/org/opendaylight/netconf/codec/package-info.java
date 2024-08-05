/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * NETCONF netty-codec integration. Exposes {@link FrameDecoder} and {@link FrameEncoder} to deal with NETCONF message
 * framing on top of a byte-oriented channel.
 */
@org.osgi.annotation.bundle.Export
package org.opendaylight.netconf.codec;
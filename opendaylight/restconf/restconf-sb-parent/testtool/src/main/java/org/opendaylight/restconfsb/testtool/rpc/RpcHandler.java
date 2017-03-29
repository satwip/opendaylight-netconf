/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.rpc;

import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface RpcHandler {
    String invokeRpc(final String identifier, NormalizedNode<?, ?> body);
}

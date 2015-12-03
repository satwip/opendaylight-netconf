/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class PATCHEntity {

    private final String operation;
    private final YangInstanceIdentifier targetNode;
    private final NormalizedNode<?,?> node;

    public PATCHEntity(final String operation, final YangInstanceIdentifier targetNode, final NormalizedNode<?, ?>
            node) {
        this.operation = operation;
        this.targetNode = targetNode;
        this.node = node;
    }

    public String getOperation() {
        return operation;
    }

    public YangInstanceIdentifier getTargetNode() {
        return targetNode;
    }

    public NormalizedNode<?, ?> getNode() {
        return node;
    }

}

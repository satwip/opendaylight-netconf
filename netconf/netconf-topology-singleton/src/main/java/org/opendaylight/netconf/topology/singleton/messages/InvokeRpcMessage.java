/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serializable;
import java.util.List;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class InvokeRpcMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    // Schema types parameters
    private final SchemaPathMessage schemaTypeMessage;

    private final NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessage(final SchemaPath type, final NormalizedNodeMessage normalizedNodeMessage) {
        this.schemaTypeMessage = new SchemaPathMessage(type);
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    public List<QName> getPath() {
        return schemaTypeMessage.getPath();
    }

    public boolean isAbsolute() {
        return schemaTypeMessage.isAbsolute();
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.controller.md.sal.dom.api.DOMActionService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public interface RemoteDeviceHandler<PREF> extends AutoCloseable {

    default void onDeviceConnected(SchemaContext remoteSchemaContext, PREF netconfSessionPreferences,
            DOMRpcService deviceRpc) {
        // DO NOTHING
    }

    default void onDeviceConnected(SchemaContext remoteSchemaContext, PREF netconfSessionPreferences,
            DOMRpcService deviceRpc, DOMActionService deviceAction) {
        // DO NOTHING
    }

    void onDeviceDisconnected();

    void onDeviceFailed(Throwable throwable);

    void onNotification(DOMNotification domNotification);

    void close();
}

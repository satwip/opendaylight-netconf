/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOperationWriteCandidateTx extends WriteCandidateTx {
    private static final Logger LOG = LoggerFactory.getLogger(CreateOperationWriteCandidateTx.class);

    public CreateOperationWriteCandidateTx(RemoteDeviceId id, NetconfBaseOps netconfOps, boolean rollbackSupport) {
        super(id, netconfOps, rollbackSupport);
    }

    public CreateOperationWriteCandidateTx(RemoteDeviceId id, NetconfBaseOps netconfOps, boolean rollbackSupport,
                                           boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    public synchronized void put(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        final String operation = "put";
        if (canPerformOperation(store, path, data, operation)) {
            final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.ofNullable(data),
                    Optional.of(ModifyAction.CREATE), path);
            editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), operation);
        }

    }
}

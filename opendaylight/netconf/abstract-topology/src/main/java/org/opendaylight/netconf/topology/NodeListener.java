/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

@Beta
public interface NodeListener extends EntityOwnershipListener {

    @Nonnull ListenableFuture<Node> nodeCreated(@Nonnull NodeId nodeId, @Nonnull Node configNode);

    @Nonnull ListenableFuture<Node> nodeUpdated(@Nonnull NodeId nodeId, @Nonnull Node configNode);

    @Nonnull ListenableFuture<Void> nodeDeleted(@Nonnull NodeId nodeId);
}

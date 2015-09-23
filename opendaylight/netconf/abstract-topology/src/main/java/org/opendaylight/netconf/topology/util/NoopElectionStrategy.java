/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.netconf.topology.ElectionStrategy;
import org.opendaylight.netconf.topology.NodeListener;

public class NoopElectionStrategy implements ElectionStrategy{
    @Override
    public void preElect(NodeListener electionCandidate) {

    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {

    }
}

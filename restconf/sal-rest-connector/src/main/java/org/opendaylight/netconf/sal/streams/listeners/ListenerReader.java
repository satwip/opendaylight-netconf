/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;

/**
 * http://stackoverflow.com/questions/6840803/simpledateformat-thread-safety
 */

public class ListenerReader implements ClusteredDOMDataTreeChangeListener {
    private ThreadLocal<ListenerRegistration<ClusteredDOMDataTreeChangeListener>> registration;
    private ThreadLocal<SettableFuture<Optional<NormalizedNode<?, ?>>>> future;
    private DOMDataTreeChangeService service;

    public Future<Optional<NormalizedNode<?,?>>> readNode(InstanceIdentifier<? extends DataObject> path) {
        future = SettableFuture.create();

        // TODO : add registration

        return future;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeCandidate> collection) {
        future.set(Optional.fromNullable());
    }
}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.BaseNodeManager.BaseNodeManagerBuilder;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.NoopRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleTopologyManagerCallback implements TopologyManagerCallback<UserDefinedMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleTopologyManagerCallback.class);

    private final DataBroker dataBroker;
    private final ActorSystem actorSystem;
    private boolean isMaster;

    private final String topologyId;
    private final List<String> remotePaths;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory;

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory) {
        this(actorSystem, dataBroker, topologyId, remotePaths, nodeHandlerFactory, new SalNodeWriter(dataBroker, topologyId));
    }

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter) {
        this(actorSystem, dataBroker, topologyId, remotePaths, nodeHandlerFactory, naSalNodeWriter, false);

    }

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter,
                                          boolean isMaster) {
        this.dataBroker = dataBroker;
        this.actorSystem = actorSystem;
        this.topologyId = topologyId;
        this.remotePaths = remotePaths;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.naSalNodeWriter = naSalNodeWriter;

        this.isMaster = isMaster;
    }

    @Override
    public ListenableFuture<Node> nodeCreated(NodeId nodeId, Node node) {
        // Init node admin and a writer for it

        // TODO let end user code notify the baseNodeManager about state changes and handle them here on topology level
        final NodeManager naBaseNodeManager =
                createNodeManager(nodeId);

        nodes.put(nodeId, naBaseNodeManager);

        // Set initial state ? in every peer or just master ? TODO
        if (isMaster) {
            naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));
        }

        // trigger connect on this node
        return naBaseNodeManager.nodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));

        // Trigger nodeUpdated only on this node
        return nodes.get(nodeId).nodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {
        // Trigger delete only on this node
        final ListenableFuture<Void> future = nodes.get(nodeId).nodeDeleted(nodeId);
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // remove proxy from node list and stop the actor
                final NodeManager remove = nodes.remove(nodeId);
                TypedActor.get(actorSystem).stop(remove);
            }

            @Override
            public void onFailure(Throwable t) {
                // NOOP will be handled on higher level
            }
        });
        return nodes.get(nodeId).nodeDeleted(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override
    public Iterable<TopologyManager<UserDefinedMessage>> getPeers() {
        // FIXME this should go through akka
        return Collections.emptySet();
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        // our post-election logic
    }

    private NodeManager createNodeManager(NodeId nodeId) {
        return new BaseNodeManagerBuilder<UserDefinedMessage>().setNodeId(nodeId.getValue())
                .setActorContext(TypedActor.context())
                .setDelegateFactory(nodeHandlerFactory)
                .setRoleChangeStrategy(new NoopRoleChangeStrategy())
                .setTopologyId(topologyId)
                .setRemotePaths(remotePaths)
                .build();
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.util.Timeout;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;

final class NetconfTopologySingletonImpl extends AbstractNetconfTopology implements AutoCloseable {
    private final RemoteDeviceId remoteDeviceId;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;
    private ActorRef masterActorRef;
//    private MasterSalFacade masterSalFacade;
    private NetconfNodeManager netconfNodeManager;
    void becomeTopologyLeader() {
        // all nodes initially register listener
        unregisterNodeManager();
        final String masterAddress = Cluster.get(setup.getActorSystem()).selfAddress().toString();
        masterActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, remoteDeviceId,
                        actorResponseWaitTime, mountPointService),
                NetconfTopologyUtils.createMasterActorName(remoteDeviceId.name(), masterAddress));
        connectNode(setup.getNode().getNodeId(), setup.getNode());
    }
    void becomeTopologyFollower() {
//        if (masterSalFacade != null) {
            // was leader before
//            masterSalFacade.setMaster(false);
//        }
        if (masterActorRef != null) {
            // was leader before
            setup.getActorSystem().stop(masterActorRef);
        }
        disconnectNode(setup.getNode().getNodeId());
        registerNodeManager();
    }
    @Override
    public void close() {
        unregisterNodeManager();

        // we expect that even leader node is going to be follower when data are deleted
        // thus we do not close connection and actor here
        // but we need to close topology and transaction chain on all nodes that were leaders
//        if (masterSalFacade != null) {
            // node was at least once leader
//            masterSalFacade.closeTopology();
//        }
    }
    private void registerNodeManager() {
        netconfNodeManager = new NetconfNodeManager(setup, remoteDeviceId, actorResponseWaitTime, mountPointService);
        netconfNodeManager.registerDataTreeChangeListener(setup.getTopologyId(), setup.getNode().key());
    }
    private void unregisterNodeManager() {
        netconfNodeManager.close();
    }
    protected NetconfTopologySingletonImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                           final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                           final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
                                           final DataBroker dataBroker, final DOMMountPointService mountPointService,
                                           final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
                                           final BaseNetconfSchemas baseSchemas, final RemoteDeviceId remoteDeviceId,
                                           final NetconfTopologySetup setup, final Timeout actorResponseWaitTime) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor, schemaManager,
                dataBroker, mountPointService, encryptionService, deviceActionFactory, baseSchemas);
        this.remoteDeviceId = remoteDeviceId;
        this.setup = setup;
        this.actorResponseWaitTime = actorResponseWaitTime;
        registerNodeManager();
    }

//    @Override
//    protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId id) {
//        masterSalFacade = new MasterSalFacade(remoteDeviceId, setup.getActorSystem(), masterActorRef,
//                actorResponseWaitTime, mountPointService, dataBroker,
//                setup.getNode().augmentation(NetconfNode.class).requireLockDatastore());
//        masterSalFacade.setMaster(true);
//        return masterSalFacade;
//    }
}
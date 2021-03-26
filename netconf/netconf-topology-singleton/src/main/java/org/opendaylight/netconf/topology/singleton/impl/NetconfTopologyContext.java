/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class NetconfTopologyContext implements ClusterSingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    private final ServiceGroupIdentifier serviceGroupIdent;
    private final Timeout actorResponseWaitTime;
    private final DOMMountPointService mountService;
    private final DeviceActionFactory deviceActionFactory;

    private NetconfTopologySetup netconfTopologyDeviceSetup;
    private RemoteDeviceId remoteDeviceId;
    private RemoteDeviceConnector remoteDeviceConnector;
    private NetconfNodeManager netconfNodeManager;
    private ActorRef masterActorRef;
    private final AtomicBoolean isMaster = new AtomicBoolean(false);

    NetconfTopologyContext(final NetconfTopologySetup netconfTopologyDeviceSetup,
            final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
            final DOMMountPointService mountService, final DeviceActionFactory deviceActionFactory) {
        this.netconfTopologyDeviceSetup = requireNonNull(netconfTopologyDeviceSetup);
        this.serviceGroupIdent = serviceGroupIdent;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.mountService = mountService;
        this.deviceActionFactory = deviceActionFactory;

        remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(netconfTopologyDeviceSetup.getNode().getNodeId(),
            netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class));
        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup, remoteDeviceId,
            deviceActionFactory);
        netconfNodeManager = createNodeDeviceManager();
    }

    @Override
    public void instantiateServiceInstance() {
        if (isMaster.compareAndSet(false, true)) {
            LOG.info("Master was selected: {}", remoteDeviceId.getHost().getIpAddress());
        } else {
            LOG.warn("The same master was already selected: {}", remoteDeviceId.getHost().getIpAddress());
        }

        // master should not listen on netconf-node operational datastore
        if (netconfNodeManager != null) {
            netconfNodeManager.close();
            netconfNodeManager = null;
        }

        // establish connections to devices
        final String masterAddress =
                Cluster.get(netconfTopologyDeviceSetup.getActorSystem()).selfAddress().toString();
        masterActorRef = netconfTopologyDeviceSetup.getActorSystem().actorOf(NetconfNodeActor.props(
                netconfTopologyDeviceSetup, remoteDeviceId, actorResponseWaitTime, mountService),
                NetconfTopologyUtils.createMasterActorName(remoteDeviceId.getName(), masterAddress));

        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup,
                remoteDeviceId, deviceActionFactory);
        remoteDeviceConnector.startRemoteDeviceConnection(newMasterSalFacade());
    }

    // called when leader is down/changed to follower
    @Override
    public ListenableFuture<?> closeServiceInstance() {
        if (isMaster.compareAndSet(true, false)) {
            LOG.info("Master was removed: {}", remoteDeviceId.getHost().getIpAddress());
        } else {
            LOG.warn("The same master was already removed: {}", remoteDeviceId.getHost().getIpAddress());
        }

        if (remoteDeviceConnector != null) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
            remoteDeviceConnector = null;
        }

        if (masterActorRef != null) {
            netconfTopologyDeviceSetup.getActorSystem().stop(masterActorRef);
            masterActorRef = null;
        }

        netconfNodeManager = createNodeDeviceManager();
        return FluentFutures.immediateNullFluentFuture();
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return serviceGroupIdent;
    }

    private NetconfNodeManager createNodeDeviceManager() {
        final NetconfNodeManager ndm =
                new NetconfNodeManager(netconfTopologyDeviceSetup, remoteDeviceId, actorResponseWaitTime, mountService);
        ndm.registerDataTreeChangeListener(netconfTopologyDeviceSetup.getTopologyId(),
                netconfTopologyDeviceSetup.getNode().key());

        return ndm;
    }

    @Override
    public void close() {
        if (netconfNodeManager != null) {
            netconfNodeManager.close();
            netconfNodeManager = null;
        }
        if (remoteDeviceConnector != null) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
            remoteDeviceConnector = null;
        }
        if (masterActorRef != null) {
            netconfTopologyDeviceSetup.getActorSystem().stop(masterActorRef);
            masterActorRef = null;
        }
    }

    /**
     * Refresh, if configuration data was changed.
     * @param setup new setup
     */
    void refresh(final @NonNull NetconfTopologySetup setup) {
        netconfTopologyDeviceSetup = requireNonNull(setup);
        remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(netconfTopologyDeviceSetup.getNode().getNodeId(),
                netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class));

        if (isMaster.get()) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
        }
        if (!isMaster.get()) {
            netconfNodeManager.refreshDevice(netconfTopologyDeviceSetup, remoteDeviceId);
        }
        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup, remoteDeviceId,
            deviceActionFactory);

        if (isMaster.get()) {
            final Future<Object> future = Patterns.ask(masterActorRef, new RefreshSetupMasterActorData(
                netconfTopologyDeviceSetup, remoteDeviceId), actorResponseWaitTime);
            future.onComplete(new OnComplete<Object>() {
                @Override
                public void onComplete(final Throwable failure, final Object success) {
                    if (failure != null) {
                        LOG.error("Failed to refresh master actor data", failure);
                        return;
                    }
                    remoteDeviceConnector.startRemoteDeviceConnection(newMasterSalFacade());
                }
            }, netconfTopologyDeviceSetup.getActorSystem().dispatcher());
        }
    }

    protected MasterSalFacade newMasterSalFacade() {
        return new MasterSalFacade(remoteDeviceId, netconfTopologyDeviceSetup.getActorSystem(), masterActorRef,
                actorResponseWaitTime, mountService, netconfTopologyDeviceSetup.getDataBroker());
    }
}
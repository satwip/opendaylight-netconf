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
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.AbstractNetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class MasterSalFacade implements AutoCloseable, RemoteDeviceHandler<NetconfSessionPreferences> {
    private static final Logger LOG = LoggerFactory.getLogger(MasterSalFacade.class);

    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final ActorRef masterActorRef;
    private final Timeout actorResponseWaitTime;
    private final NetconfDeviceSalProvider salProvider;

    private MountPointContext currentMountContext = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private DOMRpcService deviceRpc = null;
    private DOMDataBroker deviceDataBroker = null;
    private NetconfDataTreeService netconfService = null;
    private DOMActionService deviceAction = null;

    private volatile boolean closed;

    MasterSalFacade(final RemoteDeviceId id,
                    final ActorSystem actorSystem,
                    final ActorRef masterActorRef,
                    final Timeout actorResponseWaitTime,
                    final NetconfDeviceSalProvider salProvider) {
        this.id = id;
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.salProvider = salProvider;
    }

    @Override
    public void onDeviceConnected(final MountPointContext mountContext,
                                  final NetconfSessionPreferences sessionPreferences,
                                  final DOMRpcService domRpcService, final DOMActionService domActionService) {
        if (isClosed()) {
            return;
        }
        this.deviceAction = domActionService;
        LOG.debug("{}: YANG 1.1 actions are supported in clustered netconf topology, "
            + "DOMActionService exposed for the device", id);
        onDeviceConnected(mountContext, sessionPreferences, domRpcService);
    }

    @Override
    public void onDeviceConnected(final MountPointContext mountContext,
                                  final NetconfSessionPreferences sessionPreferences,
                                  final DOMRpcService domRpcService) {
        if (isClosed()) {
            return;
        }
        this.currentMountContext = mountContext;
        this.netconfSessionPreferences = sessionPreferences;
        this.deviceRpc = domRpcService;

        LOG.info("Device {} connected - registering master mount point", id);

        registerMasterMountPoint();

        sendInitialDataToActor().onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) {
                if (failure == null) {
                    updateDeviceData();
                    return;
                }

                LOG.error("{}: CreateInitialMasterActorData to {} failed", id, masterActorRef, failure);
            }
        }, actorSystem.dispatcher());
    }

    @Override
    public void onDeviceDisconnected() {
        if (isClosed()) {
            return;
        }
        LOG.info("Device {} disconnected - unregistering master mount point", id);
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(false, new NetconfDeviceCapabilities());
        unregisterMasterMountPoint();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        if (isClosed()) {
            return;
        }
        salProvider.getTopologyDatastoreAdapter().setDeviceAsFailed(throwable);
        unregisterMasterMountPoint();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        if (isClosed()) {
            return;
        }
        salProvider.getMountInstance().publish(domNotification);
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }
        unregisterMasterMountPoint();
        closed = true;
    }

    private boolean isClosed() {
        if (closed) {
            LOG.warn(this.getClass().getSimpleName() + " is already closed.");
            return true;
        }
        return false;
    }

    private void registerMasterMountPoint() {
        requireNonNull(id);
        requireNonNull(currentMountContext, "Device has no remote schema context yet. Probably not fully connected.");
        requireNonNull(netconfSessionPreferences, "Device has no capabilities yet. Probably not fully connected.");

        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();
        deviceDataBroker = newDeviceDataBroker();
        netconfService = newNetconfDataTreeService();

        // We need to create ProxyDOMDataBroker so accessing mountpoint
        // on leader node would be same as on follower node
        final ProxyDOMDataBroker proxyDataBroker = new ProxyDOMDataBroker(id, masterActorRef, actorSystem.dispatcher(),
            actorResponseWaitTime);
        final NetconfDataTreeService proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);
        salProvider.getMountInstance().onTopologyDeviceConnected(currentMountContext.getEffectiveModelContext(),
            proxyDataBroker, proxyNetconfService, deviceRpc, notificationService, deviceAction);
    }

    protected DOMDataBroker newDeviceDataBroker() {
        return new NetconfDeviceDataBroker(id, currentMountContext, deviceRpc, netconfSessionPreferences);
    }

    protected NetconfDataTreeService newNetconfDataTreeService() {
        return AbstractNetconfDataTreeService.of(id, currentMountContext, deviceRpc, netconfSessionPreferences);
    }

    private Future<Object> sendInitialDataToActor() {
        final List<SourceIdentifier> sourceIdentifiers = SchemaContextUtil.getConstituentModuleIdentifiers(
            currentMountContext.getEffectiveModelContext()).stream()
                .map(mi -> RevisionSourceIdentifier.create(mi.getName(), mi.getRevision()))
                .collect(Collectors.toList());

        LOG.debug("{}: Sending CreateInitialMasterActorData with sourceIdentifiers {} to {}", id, sourceIdentifiers,
            masterActorRef);

        // send initial data to master actor
        return Patterns.ask(masterActorRef, new CreateInitialMasterActorData(deviceDataBroker, netconfService,
            sourceIdentifiers, deviceRpc, deviceAction), actorResponseWaitTime);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void updateDeviceData() {
        final String masterAddress = Cluster.get(actorSystem).selfAddress().toString();
        LOG.debug("{}: updateDeviceData with master address {}", id, masterAddress);
        salProvider.getTopologyDatastoreAdapter().updateClusteredDeviceData(true, masterAddress,
                netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    private void unregisterMasterMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }
}

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
import java.util.List;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.AbstractNetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class MasterSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MasterSalFacade.class);

    private final RemoteDeviceId id;
    private final Timeout actorResponseWaitTime;
    private final NetconfDeviceSalProvider salProvider;
    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;

    private NetconfDeviceSchema currentSchema = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private RemoteDeviceServices deviceServices = null;
    private DOMDataBroker deviceDataBroker = null;
    private NetconfDataTreeService netconfService = null;

    MasterSalFacade(final RemoteDeviceId id,
                    final ActorSystem actorSystem,
                    final ActorRef masterActorRef,
                    final Timeout actorResponseWaitTime,
                    final DOMMountPointService mountService,
                    final DataBroker dataBroker) {
        this.id = id;
        salProvider = new NetconfDeviceSalProvider(id, mountService, dataBroker);
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        currentSchema = requireNonNull(deviceSchema);
        netconfSessionPreferences = requireNonNull(sessionPreferences);
        deviceServices = requireNonNull(services);
        if (services.actions() != null) {
            LOG.debug("{}: YANG 1.1 actions are supported in clustered netconf topology, DOMActionService exposed for "
                + "the device", id);
        }

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
        LOG.info("Device {} disconnected - unregistering master mount point", id);
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(false, NetconfDeviceCapabilities.empty());
        unregisterMasterMountPoint();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        salProvider.getTopologyDatastoreAdapter().setDeviceAsFailed(throwable);
        unregisterMasterMountPoint();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        salProvider.getMountInstance().publish(domNotification);
    }

    @Override
    public void close() {
        unregisterMasterMountPoint();
        closeGracefully(salProvider);
    }

    private void registerMasterMountPoint() {
        requireNonNull(id);

        final var mountContext = requireNonNull(currentSchema,
            "Device has no remote schema context yet. Probably not fully connected.")
            .mountContext();
        final var preferences = requireNonNull(netconfSessionPreferences,
            "Device has no capabilities yet. Probably not fully connected.");

        deviceDataBroker = newDeviceDataBroker(mountContext, preferences);
        netconfService = newNetconfDataTreeService(mountContext, preferences);

        // We need to create ProxyDOMDataBroker so accessing mountpoint
        // on leader node would be same as on follower node
        final ProxyDOMDataBroker proxyDataBroker = new ProxyDOMDataBroker(id, masterActorRef, actorSystem.dispatcher(),
            actorResponseWaitTime);
        final NetconfDataTreeService proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);
        salProvider.getMountInstance().onTopologyDeviceConnected(mountContext.getEffectiveModelContext(),
            deviceServices, proxyDataBroker, proxyNetconfService);
    }

    protected DOMDataBroker newDeviceDataBroker(final MountPointContext mountContext,
            final NetconfSessionPreferences preferences) {
        return new NetconfDeviceDataBroker(id, mountContext, deviceServices.rpcs(), preferences,
            // FIXME: pass down lock flag
            null);
    }

    protected NetconfDataTreeService newNetconfDataTreeService(final MountPointContext mountContext,
            final NetconfSessionPreferences preferences) {
        return AbstractNetconfDataTreeService.of(id, mountContext, deviceServices.rpcs(), preferences,
            // FIXME: pass down lock flag
            null);
    }

    private Future<Object> sendInitialDataToActor() {
        final List<SourceIdentifier> sourceIdentifiers = List.copyOf(SchemaContextUtil.getConstituentModuleIdentifiers(
            currentSchema.mountContext().getEffectiveModelContext()));

        LOG.debug("{}: Sending CreateInitialMasterActorData with sourceIdentifiers {} to {}", id, sourceIdentifiers,
            masterActorRef);

        // send initial data to master actor
        return Patterns.ask(masterActorRef, new CreateInitialMasterActorData(deviceDataBroker, netconfService,
            sourceIdentifiers, deviceServices), actorResponseWaitTime);
    }

    private void updateDeviceData() {
        final String masterAddress = Cluster.get(actorSystem).selfAddress().toString();
        LOG.debug("{}: updateDeviceData with master address {}", id, masterAddress);
        salProvider.getTopologyDatastoreAdapter().updateClusteredDeviceData(true, masterAddress,
            currentSchema.capabilities());
    }

    private void unregisterMasterMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void closeGracefully(final AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (final Exception e) {
                LOG.error("{}: Ignoring exception while closing {}", id, resource, e);
            }
        }
    }
}

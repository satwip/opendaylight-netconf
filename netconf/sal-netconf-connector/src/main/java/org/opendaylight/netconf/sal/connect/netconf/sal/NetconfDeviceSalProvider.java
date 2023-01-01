/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.NetconfRpcService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.api.SchemalessRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: remove this class and promote MountInstance to a top-level construct
// Non-final for mocking
public class NetconfDeviceSalProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalProvider.class);

    private final RemoteDeviceId id;
    private final MountInstance mountInstance;

    public NetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService) {
        id = requireNonNull(deviceId);
        mountInstance = new MountInstance(mountService, id);
    }

    public MountInstance getMountInstance() {
        checkState(mountInstance != null, "%s: Mount instance was not initialized by sal. Cannot get mount instance",
                id);
        return mountInstance;
    }

    @Override
    public void close() {
        mountInstance.close();
    }

    public static class MountInstance implements AutoCloseable {

        private final DOMMountPointService mountService;
        private final RemoteDeviceId id;

        private NetconfDeviceNotificationService notificationService;
        private ObjectRegistration<DOMMountPoint> topologyRegistration;

        MountInstance(final DOMMountPointService mountService, final RemoteDeviceId id) {
            this.mountService = requireNonNull(mountService);
            this.id = requireNonNull(id);
        }

        public void onTopologyDeviceConnected(final EffectiveModelContext initialCtx,
                final RemoteDeviceServices services, final DOMDataBroker broker,
                final NetconfDataTreeService dataTreeService) {
            onTopologyDeviceConnected(initialCtx, services, new NetconfDeviceNotificationService(), broker,
                dataTreeService);
        }

        public synchronized void onTopologyDeviceConnected(final EffectiveModelContext initialCtx,
                final RemoteDeviceServices services, final NetconfDeviceNotificationService newNotificationService,
                final DOMDataBroker broker, final NetconfDataTreeService dataTreeService) {
            requireNonNull(mountService, "Closed");
            checkState(topologyRegistration == null, "Already initialized");

            final var mountBuilder = mountService.createMountPoint(id.getTopologyPath());
            mountBuilder.addService(DOMSchemaService.class, FixedDOMSchemaService.of(() -> initialCtx));

            final var rpcs = services.rpcs();
            mountBuilder.addService(NetconfRpcService.class, rpcs);
            if (rpcs instanceof Rpcs.Normalized normalized) {
                mountBuilder.addService(DOMRpcService.class, normalized);
            } else if (rpcs instanceof Rpcs.Schemaless schemaless) {
                mountBuilder.addService(SchemalessRpcService.class, schemaless);
            }
            if (services.actions() instanceof Actions.Normalized normalized) {
                mountBuilder.addService(DOMActionService.class, normalized);
            }

            if (broker != null) {
                mountBuilder.addService(DOMDataBroker.class, broker);
            }
            if (dataTreeService != null) {
                mountBuilder.addService(NetconfDataTreeService.class, dataTreeService);
            }
            mountBuilder.addService(DOMNotificationService.class, newNotificationService);
            notificationService = newNotificationService;

            topologyRegistration = mountBuilder.register();
            LOG.debug("{}: TOPOLOGY Mountpoint exposed into MD-SAL {}", id, topologyRegistration);
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        public synchronized void onTopologyDeviceDisconnected() {
            if (topologyRegistration == null) {
                LOG.trace("{}: Not removing TOPOLOGY mountpoint from MD-SAL, mountpoint was not registered yet", id);
                return;
            }

            try {
                topologyRegistration.close();
            } catch (final Exception e) {
                // Only log and ignore
                LOG.warn("Unable to unregister mount instance for {}. Ignoring exception", id.getTopologyPath(), e);
            } finally {
                LOG.debug("{}: TOPOLOGY Mountpoint removed from MD-SAL {}", id, topologyRegistration);
                topologyRegistration = null;
            }
        }

        @Override
        public synchronized void close() {
            onTopologyDeviceDisconnected();
        }

        public synchronized void publish(final DOMNotification domNotification) {
            checkNotNull(notificationService, "Device not set up yet, cannot handle notification %s", domNotification)
                .publishNotification(domNotification);
        }
    }

}

/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.odl.device.notification;

import java.net.URISyntaxException;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ApiPathCanonizer;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev240218.SubscribeDeviceNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev240218.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev240218.SubscribeDeviceNotificationOutput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF implementation of {@link SubscribeDeviceNotification}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public final class SubscribeDeviceNotificationRpc extends RpcImplementation
        implements DOMMountPointListener, AutoCloseable  {
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "path").intern());
    private static final NodeIdentifier DEVICE_NOTIFICATION_STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "stream-name").intern());
    private static final Logger LOG = LoggerFactory.getLogger(SubscribeDeviceNotificationRpc.class);

    private final DOMMountPointService mountPointService;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public SubscribeDeviceNotificationRpc(@Reference final RestconfStream.Registry streamRegistry,
                                          @Reference final DOMMountPointService mountPointService) {
        super(SubscribeDeviceNotification.QNAME);
        this.mountPointService = requireNonNull(mountPointService);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.mountPointService.registerProvisionListener(this);
    }

    @Override
    public void invoke(final ServerRequest<ContainerNode> request, final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final var pathLeaf = body.childByArg(DEVICE_NOTIFICATION_PATH_NODEID);
        if (pathLeaf == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No path specified"));
            return;
        }
        final var pathLeafBody = pathLeaf.body();
        if (!(pathLeafBody instanceof YangInstanceIdentifier path)) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "Unexpected path %s", pathLeafBody));
            return;
        }
        if (!(path.getLastPathArgument() instanceof NodeIdentifierWithPredicates listId)) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT,
                "%s does not refer to a list item", path));
            return;
        }
        if (listId.size() != 1) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "%s uses multiple keys", path));
            return;
        }

        final ApiPath apiPath;
        try {
            apiPath = new ApiPathCanonizer(input.path().databind()).dataToApiPath(path);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }

        streamRegistry.createStream(
            request.transform(stream -> ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(SubscribeDeviceNotificationOutput.QNAME))
                .withChild(ImmutableNodes.leafNode(DEVICE_NOTIFICATION_STREAM_NAME_NODEID, stream.name()))
                .build()),
            restconfURI, new DeviceNotificationSource(mountPointService, path),
            "All YANG notifications occuring on mount point /" + apiPath.toString());
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public void onMountPointCreated(DOMMountPoint mountPoint) {
        createDeviceNotificationListenerOnMountPoint(mountPoint.getIdentifier());
    }

    @Override
    public void onMountPointRemoved(YangInstanceIdentifier path) {

    }

    private void createDeviceNotificationListenerOnMountPoint(final YangInstanceIdentifier path) {
        String nodeId = "";
        try {
            if (path.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                final NodeIdentifierWithPredicates node =
                        ((NodeIdentifierWithPredicates) path.getLastPathArgument());
                nodeId = node.getValue(QName.create(node.getNodeType(), "node-id"), String.class);
            }
            streamRegistry.createStream(new DeviceNotificationSource(mountPointService, path),
                    "Stream created for node " + nodeId);
        } catch (URISyntaxException e) {
            LOG.error("Failed to crete stream for nodeId {} cause {}", nodeId, e.getMessage());
        }
    }

}
/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.networks.network.network.types.NetconfNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

public final class RemoteDeviceId {
    private static final String DEFAULT_TOPOLOGY_NAME = NetconfNetwork.QNAME.getLocalName();
    private static final KeyedInstanceIdentifier<Network, NetworkKey> DEFAULT_TOPOLOGY_IID =
            InstanceIdentifier.create(Networks.class)
            .child(Network.class, new NetworkKey(new NetworkId(DEFAULT_TOPOLOGY_NAME)));
    private static final YangInstanceIdentifier DEFAULT_TOPOLOGY_NODE = YangInstanceIdentifier.builder()
            .node(Networks.QNAME).node(Network.QNAME)
            .nodeWithKey(Network.QNAME, QName.create(Network.QNAME, "network-id"), DEFAULT_TOPOLOGY_NAME)
            .node(Node.QNAME)
            .build();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();

    private final String name;
    private final NodeKey key;
    private final YangInstanceIdentifier topologyPath;
    private final KeyedInstanceIdentifier<Node, NodeKey> topologyBindingPath;
    private InetSocketAddress address;
    private Host host;

    private RemoteDeviceId(final String name) {
        this.name = Preconditions.checkNotNull(name);
        this.topologyPath = DEFAULT_TOPOLOGY_NODE
                .node(new NodeIdentifierWithPredicates(Node.QNAME, NODE_ID_QNAME, name));
        this.key = new NodeKey(new NodeId(name));
        this.topologyBindingPath = DEFAULT_TOPOLOGY_IID.child(Node.class, key);
    }

    public RemoteDeviceId(final String name, final InetSocketAddress address) {
        this(name);
        this.address = address;
        this.host = buildHost();
    }

    private Host buildHost() {
        return HostBuilder.getDefaultInstance(address.getHostString());
    }

    public String getName() {
        return name;
    }

    public NodeKey getBindingKey() {
        return key;
    }

    public InstanceIdentifier<Node> getTopologyBindingPath() {
        return topologyBindingPath;
    }

    public YangInstanceIdentifier getTopologyPath() {
        return topologyPath;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "RemoteDevice{" + name + '}';
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RemoteDeviceId)) {
            return false;
        }
        final RemoteDeviceId that = (RemoteDeviceId) obj;
        return name.equals(that.name) && topologyBindingPath.equals(that.topologyBindingPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, topologyBindingPath);
    }
}

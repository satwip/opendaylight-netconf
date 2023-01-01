/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

public final class RemoteDeviceId {
    // FIXME: extract all of this to users, as they are in control of topology-id
    private static final String DEFAULT_TOPOLOGY_NAME = TopologyNetconf.QNAME.getLocalName();
    private static final YangInstanceIdentifier DEFAULT_TOPOLOGY_NODE = YangInstanceIdentifier.builder()
            .node(NetworkTopology.QNAME).node(Topology.QNAME)
            .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), DEFAULT_TOPOLOGY_NAME)
            .node(Node.QNAME)
            .build();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();

    // FIXME: extract this into caller and pass to constructor
    @VisibleForTesting
    public static final KeyedInstanceIdentifier<Topology, TopologyKey> DEFAULT_TOPOLOGY_IID =
        InstanceIdentifier.create(NetworkTopology.class)
        .child(Topology.class, new TopologyKey(new TopologyId(DEFAULT_TOPOLOGY_NAME)));

    private final @NonNull String name;
    private final @NonNull YangInstanceIdentifier topologyPath;

    private InetSocketAddress address;
    private Host host;

    private RemoteDeviceId(final String name) {
        this.name = requireNonNull(name);
        topologyPath = DEFAULT_TOPOLOGY_NODE.node(NodeIdentifierWithPredicates.of(Node.QNAME, NODE_ID_QNAME, name));
    }

    public RemoteDeviceId(final String name, final InetSocketAddress address) {
        this(name);
        this.address = address;

        final var addr = address.getAddress();
        host = addr != null ? new Host(IetfInetUtil.INSTANCE.ipAddressFor(addr))
            : new Host(new DomainName(address.getHostString()));
    }

    public @NonNull String getName() {
        return name;
    }

    public @NonNull YangInstanceIdentifier getTopologyPath() {
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
        return this == obj || obj instanceof RemoteDeviceId other
            && name.equals(other.name) && topologyPath.equals(other.topologyPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, topologyPath);
    }
}

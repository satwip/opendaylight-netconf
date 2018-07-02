/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;
import javassist.ClassPool;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.impl.BindingDOMDataBrokerAdapter;
import org.opendaylight.controller.md.sal.binding.impl.BindingToNormalizedNodeCodec;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.broker.impl.SerializedDOMDataBroker;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.DataObjectSerializerGenerator;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.NetconfNodeConnectionStatus;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class NetconfDeviceTopologyAdapterTest {

    private final RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private DataBroker broker;
    @Mock
    private WriteTransaction writeTx;
    @Mock
    private BindingTransactionChain txChain;
    @Mock
    private NetconfNode data;

    private final String txIdent = "test transaction";

    private SchemaContext schemaContext = null;
    private final String sessionIdForReporting = "netconf-test-session1";

    private BindingTransactionChain transactionChain;

    private DataBroker dataBroker;

    private DOMDataBroker domDataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(txChain).when(broker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(writeTx)
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        doNothing().when(writeTx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));

        doReturn(txIdent).when(writeTx).getIdentifier();

        this.schemaContext = YangParserTestUtils.parseYangResources(NetconfDeviceTopologyAdapterTest.class,
            "/schemas/ietf-network@2018-02-26.yang", "/schemas/ietf-inet-types@2013-07-15.yang",
            "/schemas/yang-ext.yang", "/schemas/netconf-node-topology.yang",
            "/schemas/network-topology-augment-test@2016-08-08.yang");
        schemaContext.getModules();
        final DOMSchemaService schemaService = createSchemaService();

        final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

        final EnumMap<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        domDataBroker = new SerializedDOMDataBroker(datastores, MoreExecutors.newDirectExecutorService());

        final ClassPool pool = ClassPool.getDefault();
        final DataObjectSerializerGenerator generator = StreamWriterGenerator.create(JavassistUtils.forClassPool(pool));
        final BindingNormalizedNodeCodecRegistry codecRegistry = new BindingNormalizedNodeCodecRegistry(generator);
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        codecRegistry.onBindingRuntimeContextUpdated(
                BindingRuntimeContext.create(moduleInfoBackedContext, schemaContext));

        final GeneratedClassLoadingStrategy loading = GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy();
        final BindingToNormalizedNodeCodec bindingToNormalized =
                new BindingToNormalizedNodeCodec(loading, codecRegistry);
        bindingToNormalized.onGlobalContextUpdated(schemaContext);
        dataBroker = new BindingDOMDataBrokerAdapter(domDataBroker, bindingToNormalized);

        transactionChain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                    final AsyncTransaction<?, ?> transaction, final Throwable cause) {

            }

            @Override
            public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {

            }
        });

    }

    @Test
    public void testFailedDevice() throws Exception {

        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.setDeviceAsFailed(null);

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        adapter.close();

        adapter = new NetconfDeviceTopologyAdapter(id, transactionChain); //not a mock
        adapter.setDeviceAsFailed(null);

        Optional<NetconfNode> netconfNode = dataBroker.newReadWriteTransaction().read(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class)).checkedGet(5, TimeUnit.SECONDS);

        assertEquals("Netconf node should be presented.", true, netconfNode.isPresent());
        assertEquals("Connection status should be failed.",
                NetconfNodeConnectionStatus.ConnectionStatus.UnableToConnect.getName(),
                netconfNode.get().getConnectionStatus().getName());

    }

    @Test
    public void testDeviceUpdate() throws Exception {
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx, times(1))
                .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(NetconfNode.class));
        verify(writeTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));

    }

    @Test
    public void testDeviceAugmentedNodePresence() throws Exception {

        Integer dataTestId = 474747;

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, transactionChain);

        QName netconfTestLeafQname = QName.create(
                "urn:TBD:params:xml:ns:yang:network-topology-augment-test", "2016-08-08", "test-id").intern();

        YangInstanceIdentifier pathToAugmentedLeaf = YangInstanceIdentifier.builder().node(Networks.QNAME)
                .node(Network.QNAME)
                .nodeWithKey(Network.QNAME, QName.create(Network.QNAME, "network-id"), "topology-netconf")
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), "test")
                .node(netconfTestLeafQname).build();

        NormalizedNode<?, ?> augmentNode = ImmutableLeafNodeBuilder.create().withValue(dataTestId)
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(netconfTestLeafQname)).build();

        DOMDataWriteTransaction wtx =  domDataBroker.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf, augmentNode);
        wtx.submit().get(5, TimeUnit.SECONDS);

        adapter.updateDeviceData(true, new NetconfDeviceCapabilities());
        Optional<NormalizedNode<?, ?>> testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device update.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before update node.", dataTestId, testNode.get().getValue());

        adapter.setDeviceAsFailed(null);
        testNode = domDataBroker.newReadOnlyTransaction()
                .read(LogicalDatastoreType.OPERATIONAL, pathToAugmentedLeaf).checkedGet(2, TimeUnit.SECONDS);

        assertEquals("Augmented node data should be still present after device failed.", true, testNode.isPresent());
        assertEquals("Augmented data should be the same as before failed device.",
                dataTestId, testNode.get().getValue());
    }

    private DOMSchemaService createSchemaService() {
        return new DOMSchemaService() {

            @Override
            public SchemaContext getSessionContext() {
                return schemaContext;
            }

            @Override
            public SchemaContext getGlobalContext() {
                return schemaContext;
            }

            @Override
            public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(
                    final SchemaContextListener listener) {
                listener.onGlobalContextUpdated(getGlobalContext());
                return new AbstractListenerRegistration<SchemaContextListener>(listener) {
                    @Override
                    protected void removeRegistration() {
                        // No-op
                    }
                };
            }
        };
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();

        NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(id, txChain);
        adapter.close();

        verify(txChain, times(2)).newWriteOnlyTransaction();
        verify(writeTx).delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        verify(writeTx, times(2)).submit();
    }

}

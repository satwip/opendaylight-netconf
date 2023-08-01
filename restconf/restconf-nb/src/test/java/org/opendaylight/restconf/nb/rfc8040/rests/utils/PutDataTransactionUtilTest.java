/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PutDataTransactionUtilTest extends AbstractJukeboxTest {
    private static final ContainerNode JUKEBOX_WITH_BANDS = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(BAND_ENTRY)
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description 2"))
                .build())
            .build())
        .build();

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private DOMDataTreeWriteTransaction write;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    @Before
    public void before() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
    }

    @Test
    public void testPutContainerData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new MdsalRestconfStrategy(mockDataBroker), WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
    }

    @Test
    public void testPutCreateContainerData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).lock();
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPutReplaceContainerData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX,
            Optional.empty());
    }

    @Test
    public void testPutLeafData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture()).when(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new MdsalRestconfStrategy(mockDataBroker),
            WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF);
    }

    @Test
    public void testPutCreateLeafData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutReplaceLeafData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(GAP_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());

        PutDataTransactionUtil.putData(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA, new NetconfRestconfStrategy(netconfService),
            WriteDataParams.empty());
        verify(netconfService).getConfig(GAP_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, GAP_IID, GAP_LEAF, Optional.empty());
    }

    @Test
    public void testPutListData() {
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(immediateFalseFluentFuture())
                .when(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new MdsalRestconfStrategy(mockDataBroker), WriteDataParams.empty());
        verify(read).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS);
    }

    @Test
    public void testPutCreateListData() {
        doReturn(immediateFluentFuture(Optional.empty())).when(netconfService).getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }

    @Test
    public void testPutReplaceListData() {
        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(netconfService)
            .getConfig(JUKEBOX_IID);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS, Optional.empty());

        PutDataTransactionUtil.putData(JUKEBOX_IID, JUKEBOX_WITH_BANDS, JUKEBOX_SCHEMA,
            new NetconfRestconfStrategy(netconfService), WriteDataParams.empty());
        verify(netconfService).getConfig(JUKEBOX_IID);
        verify(netconfService).replace(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, JUKEBOX_WITH_BANDS,
            Optional.empty());
    }
}

/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class ReadTransactionActorTest {

    private static final YangInstanceIdentifier path = YangInstanceIdentifier.EMPTY;
    private static final LogicalDatastoreType store = LogicalDatastoreType.CONFIGURATION;

    @Mock
    private DOMDataReadOnlyTransaction deviceReadTx;
    private TestProbe probe;
    private ActorSystem system;
    private TestActorRef<ReadTransactionActor> actorRef;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        system = ActorSystem.apply();
        probe = TestProbe.apply(system);
        actorRef = TestActorRef.create(system, ReadTransactionActor.props(deviceReadTx), "testA");
    }

    @Test
    public void testRead() throws Exception {
        final ContainerNode node = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("cont")))
                .build();
        when(deviceReadTx.read(store, path)).thenReturn(Futures.immediateCheckedFuture(Optional.of(node)));
        actorRef.tell(new ReadRequest(store, path), probe.ref());
        verify(deviceReadTx).read(store, path);
        probe.expectMsgClass(NormalizedNodeMessage.class);
    }

    @Test
    public void testReadEmpty() throws Exception {
        when(deviceReadTx.read(store, path)).thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
        actorRef.tell(new ReadRequest(store, path), probe.ref());
        verify(deviceReadTx).read(store, path);
        probe.expectMsgClass(EmptyReadResponse.class);
    }

    @Test
    public void testReadFailure() throws Exception {
        final ReadFailedException cause = new ReadFailedException("fail");
        when(deviceReadTx.read(store, path)).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        actorRef.tell(new ReadRequest(store, path), probe.ref());
        verify(deviceReadTx).read(store, path);
        probe.expectMsg(cause);
    }

    @Test
    public void testExists() throws Exception {
        when(deviceReadTx.exists(store, path)).thenReturn(Futures.immediateCheckedFuture(true));
        actorRef.tell(new ExistsRequest(store, path), probe.ref());
        verify(deviceReadTx).exists(store, path);
        probe.expectMsg(true);
    }

    @Test
    public void testExistsFailure() throws Exception {
        final ReadFailedException cause = new ReadFailedException("fail");
        when(deviceReadTx.exists(store, path)).thenReturn(Futures.immediateFailedCheckedFuture(cause));
        actorRef.tell(new ExistsRequest(store, path), probe.ref());
        verify(deviceReadTx).exists(store, path);
        probe.expectMsg(cause);
    }

    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(system);

    }
}
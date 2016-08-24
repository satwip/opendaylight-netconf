/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class TestData {

    public final YangInstanceIdentifier path;
    public final YangInstanceIdentifier path2;
    public final YangInstanceIdentifier path3;
    public final MapEntryNode data;
    public final MapEntryNode data2;
    public final ContainerNode data3;
    public final ContainerNode data4;
    public final MapNode listData;
    public final MapNode listData2;
    public final UnkeyedListEntryNode unkeyedListEntryNode;
    public final LeafNode contentLeaf;
    public final LeafNode contentLeaf2;
    public final MapEntryNode checkData;

    public TestData() {
        final QName base = QName.create("ns", "2016-02-28", "base");
        final QName listQname = QName.create(base, "list");
        final QName listKeyQName = QName.create(base, "list-key");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQName, "keyValue");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey2 =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQName, "keyValue2");
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "leaf-content")))
                .withValue("content")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "leaf-content-different")))
                .withValue("content-different")
                .build();
        final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> dataContainer = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "identifier")))
                .withValue("id")
                .build();
        unkeyedListEntryNode = Builders.unkeyedListEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(dataContainer)
                .build();
        data = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .build();
        data2 = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content2)
                .build();
        checkData = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content2)
                .withChild(content)
                .build();
        listData = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .build();
        listData2 = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(listQname, "list")))
                .withChild(data)
                .withChild(data2)
                .build();
        path = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .node(nodeWithKey)
                .build();
        path2 = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .node(nodeWithKey2)
                .build();
        path3 = YangInstanceIdentifier.builder()
                .node(QName.create(base, "cont"))
                .node(listQname)
                .build();
        contentLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "content")))
                .withValue("test")
                .build();
        contentLeaf2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "content2")))
                .withValue("test2")
                .build();
        data3 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "container")))
                .withChild(contentLeaf)
                .build();
        data4 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(base, "container2")))
                .withChild(contentLeaf2)
                .build();
    }
}
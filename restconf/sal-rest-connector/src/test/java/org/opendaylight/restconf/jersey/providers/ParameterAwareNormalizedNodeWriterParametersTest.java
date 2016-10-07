/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * Unit test for {@link ParameterAwareNormalizedNodeWriter} used with all parameters.
 */
public class ParameterAwareNormalizedNodeWriterParametersTest {

    @Mock
    private NormalizedNodeStreamWriter writer;
    @Mock
    private ContainerNode containerNodeData;
    @Mock
    private LeafSetNode<String> leafSetNodeData;
    @Mock
    private LeafSetEntryNode<String> leafSetEntryNodeData;

    private NodeIdentifier containerNodeIdentifier;
    private NodeIdentifier leafSetNodeIdentifier;
    private NodeWithValue<?> leafSetEntryNodeIdentifier;

    private Collection<DataContainerChild<?, ?>> containerNodeValue;
    private Collection<LeafSetEntryNode<String>> leafSetNodeValue;
    private String leafSetEntryNodeValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // identifiers
        containerNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "container"));
        Mockito.when(containerNodeData.getIdentifier()).thenReturn(containerNodeIdentifier);

        final QName leafSetEntryNodeQName = QName.create("namespace", "leaf-set-entry");
        leafSetEntryNodeValue = "leaf-set-value";
        leafSetEntryNodeIdentifier = new NodeWithValue<>(leafSetEntryNodeQName, leafSetEntryNodeValue);
        Mockito.when(leafSetEntryNodeData.getIdentifier()).thenReturn(leafSetEntryNodeIdentifier);
        Mockito.when(leafSetEntryNodeData.getNodeType()).thenReturn(leafSetEntryNodeIdentifier.getNodeType());
        Mockito.when(leafSetEntryNodeData.getNodeType()).thenReturn(leafSetEntryNodeQName);

        leafSetNodeIdentifier = NodeIdentifier.create(QName.create("namespace", "leaf-set"));
        Mockito.when(leafSetNodeData.getIdentifier()).thenReturn(leafSetNodeIdentifier);
        Mockito.when(leafSetNodeData.getNodeType()).thenReturn(leafSetNodeIdentifier.getNodeType());

        // values
        Mockito.when(leafSetEntryNodeData.getValue()).thenReturn(leafSetEntryNodeValue);

        leafSetNodeValue = Collections.singletonList(leafSetEntryNodeData);
        Mockito.when(leafSetNodeData.getValue()).thenReturn(leafSetNodeValue);

        containerNodeValue = Collections.singleton(leafSetNodeData);
        Mockito.when(containerNodeData.getValue()).thenReturn(containerNodeValue);
    }

    /**
     * Test write {@link ContainerNode} when all its children are selected to be written by fields parameter.
     * Depth parameter is also used and limits output to depth 1.
     * Fields parameter has effect limiting depth parameter in way that selected nodes and its children are
     * written regardless of their depth.
     * Fields parameter selects 1/1 of container children to be written.
     */
    @Test
    public void writeContainerParameterPrioritiesTest() throws Exception {
        final List<Set<QName>> limitedFields = new ArrayList<>();
        limitedFields.add(Sets.newHashSet(leafSetNodeIdentifier.getNodeType()));

        final ParameterAwareNormalizedNodeWriter parameterWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                writer, null, limitedFields);

        parameterWriter.write(containerNodeData);

        final InOrder inOrder = Mockito.inOrder(writer);
        inOrder.verify(writer, Mockito.times(1)).startContainerNode(containerNodeIdentifier, containerNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).startLeafSet(leafSetNodeIdentifier, leafSetNodeValue.size());
        inOrder.verify(writer, Mockito.times(1)).leafSetEntryNode(
                leafSetEntryNodeIdentifier.getNodeType(), leafSetEntryNodeValue);
        inOrder.verify(writer, Mockito.times(2)).endNode();
        Mockito.verifyNoMoreInteractions(writer);
    }
}
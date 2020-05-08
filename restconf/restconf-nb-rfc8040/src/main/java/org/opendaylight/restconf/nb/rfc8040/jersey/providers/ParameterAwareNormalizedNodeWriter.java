/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter.UNKNOWN_SIZE;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an experimental iterator over a {@link NormalizedNode}. This is essentially
 * the opposite of a {@link javax.xml.stream.XMLStreamReader} -- unlike instantiating an iterator over
 * the backing data, this encapsulates a {@link NormalizedNodeStreamWriter} and allows
 * us to write multiple nodes.
 */
@Beta
public class ParameterAwareNormalizedNodeWriter implements RestconfNormalizedNodeWriter {
    private static final QName ROOT_DATA_QNAME = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data");

    private final NormalizedNodeStreamWriter writer;
    private final Integer maxDepth;
    protected final List<Set<QName>> fields;
    protected int currentDepth = 0;
    protected final List<String> parentChildRelation;
    protected String parentNode = "";

    private ParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final Integer maxDepth,
                                               final List<Set<QName>> fields,
                                               final List<String> parentChildRelation) {
        this.writer = requireNonNull(writer);
        this.maxDepth = maxDepth;
        this.fields = fields;
        this.parentChildRelation = parentChildRelation;
    }

    protected final NormalizedNodeStreamWriter getWriter() {
        return writer;
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @param parentChildRelation Contain parent and child relation
     * @return A new instance.
     */
    public static ParameterAwareNormalizedNodeWriter forStreamWriter(
            final NormalizedNodeStreamWriter writer, final Integer maxDepth,
            final List<Set<QName>> fields, final List<String> parentChildRelation) {
        return forStreamWriter(writer, true,  maxDepth, fields, parentChildRelation);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}. Unlike the simple
     * {@link #forStreamWriter(NormalizedNodeStreamWriter, Integer, List, List)}
     * method, this allows the caller to switch off RFC6020 XML compliance, providing better
     * throughput. The reason is that the XML mapping rules in RFC6020 require the encoding
     * to emit leaf nodes which participate in a list's key first and in the order in which
     * they are defined in the key. For JSON, this requirement is completely relaxed and leaves
     * can be ordered in any way we see fit. The former requires a bit of work: first a lookup
     * for each key and then for each emitted node we need to check whether it was already
     * emitted.
     *
     * @param writer Back-end writer
     * @param orderKeyLeaves whether the returned instance should be RFC6020 XML compliant.
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @param parentChildRelation Contain parent and child relation
     * @return A new instance.
     */
    public static ParameterAwareNormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
                                                                     final boolean orderKeyLeaves,
                                                                     final Integer maxDepth,
                                                                     final List<Set<QName>> fields,
                                                                     final List<String> parentChildRelation) {
        return orderKeyLeaves ? new OrderedParameterAwareNormalizedNodeWriter(writer, maxDepth,
            fields, parentChildRelation)
                : new ParameterAwareNormalizedNodeWriter(writer, maxDepth, fields, parentChildRelation);
    }

    /**
     * Iterate over the provided {@link NormalizedNode} and emit write
     * events to the encapsulated {@link NormalizedNodeStreamWriter}.
     *
     * @param node Node
     * @return {@code ParameterAwareNormalizedNodeWriter}
     * @throws IOException when thrown from the backing writer.
     */
    @Override
    public final ParameterAwareNormalizedNodeWriter write(final NormalizedNode<?, ?> node) throws IOException {
        if (wasProcessedAsCompositeNode(node)) {
            return this;
        }

        if (wasProcessAsSimpleNode(node)) {
            return this;
        }

        throw new IllegalStateException("It wasn't possible to serialize node " + node);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    /**
     * Emit a best guess of a hint for a particular set of children. It evaluates the
     * iterable to see if the size can be easily gotten to. If it is, we hint at the
     * real number of child nodes. Otherwise we emit UNKNOWN_SIZE.
     *
     * @param children Child nodes
     * @return Best estimate of the collection size required to hold all the children.
     */
    static final int childSizeHint(final Iterable<?> children) {
        return children instanceof Collection ? ((Collection<?>) children).size() : UNKNOWN_SIZE;
    }

    private boolean wasProcessAsSimpleNode(final NormalizedNode<?, ?> node) throws IOException {
        if (node instanceof LeafSetEntryNode) {
            if (selectedByParameters(node, false)) {
                final LeafSetEntryNode<?> nodeAsLeafList = (LeafSetEntryNode<?>) node;
                writer.startLeafSetEntryNode(nodeAsLeafList.getIdentifier());
                writer.scalarValue(nodeAsLeafList.getValue());
                writer.endNode();
            }
            return true;
        } else if (node instanceof LeafNode) {
            final LeafNode<?> nodeAsLeaf = (LeafNode<?>)node;
            writer.startLeafNode(nodeAsLeaf.getIdentifier());
            writer.scalarValue(nodeAsLeaf.getValue());
            writer.endNode();
            return true;
        } else if (node instanceof AnyxmlNode) {
            final AnyxmlNode<?> anyxmlNode = (AnyxmlNode<?>)node;
            final Class<?> objectModel = anyxmlNode.getValueObjectModel();
            if (writer.startAnyxmlNode(anyxmlNode.getIdentifier(), objectModel)) {
                if (DOMSource.class.isAssignableFrom(objectModel)) {
                    writer.domSourceValue((DOMSource) anyxmlNode.getValue());
                } else {
                    writer.scalarValue(anyxmlNode.getValue());
                }
                writer.endNode();
            }
            return true;
        }

        return false;
    }

    /**
     * Check if node should be written according to parameters fields and depth.
     * See <a href="https://tools.ietf.org/html/draft-ietf-netconf-restconf-18#page-49">Restconf draft</a>.
     * @param node Node to be written
     * @param mixinParent {@code true} if parent is mixin, {@code false} otherwise
     * @return {@code true} if node will be written, {@code false} otherwise
     */
    protected boolean selectedByParameters(final NormalizedNode<?, ?> node, final boolean mixinParent) {
        // nodes to be written are not limited by fields, only by depth
        if (fields == null) {
            return maxDepth == null || currentDepth < maxDepth;
        }

        // children of mixin nodes are never selected in fields but must be written if they are first in selected target
        if (mixinParent && currentDepth == 0) {
            return true;
        }

        // always write augmentation nodes
        if (node instanceof AugmentationNode) {
            return true;
        }

        // write only selected nodes
        if (currentDepth > 0 && currentDepth <= fields.size()) {
            for (Set<QName> field : fields) {
                if (field.contains(node.getNodeType())) {
                    return true;
                }
            }
            return false;
        }

        // after this depth only depth parameter is used to determine when to write node
        return maxDepth == null || currentDepth < maxDepth;
    }

    /**
     * Verify relation between parent and child using parentChildRelation list.
     *
     * @param parent Parent node name
     * @param child Child node name
     * @return {@code true} if child and parent relation is correct, {@code false} otherwise
     */
    protected final boolean checkParentChildRelation(String parent, String child) {
        if (parentChildRelation == null || parent.isBlank()) {
            return true;
        }
        else if (parent.equalsIgnoreCase(child)) {
            return true;
        }
        else {
            if (parentChildRelation.contains(parent + "#" + child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decide all children need to write or not.
     *
     * @param children Child iterable
     * @return {@code false} if child exists in parentChildRelation field list, {@code true} otherwise
    */
    protected final boolean writeAllChildren(final Iterable<? extends NormalizedNode<?, ?>> children) {
        if (parentChildRelation == null) {
            return true;
        }
        else {
            String[] arrOfStr;
            boolean positionAsParent = false;
            boolean positionAsChild = false;
            for (final NormalizedNode<?, ?> child : children) {
                for (String str : parentChildRelation) {
                    arrOfStr = str.split("#");
                    if (arrOfStr[0].equalsIgnoreCase(child.getIdentifier().getNodeType().getLocalName())) {
                        return false;
                    }
                    if (arrOfStr[1].equalsIgnoreCase(child.getIdentifier().getNodeType().getLocalName())) {
                        return false;
                    }
                }
            }
            if (positionAsParent == false && positionAsChild == false) {
                return true;
            }
            return false;
        }
    }

    /**
     * Emit events for all children and then emit an endNode() event.
     *
     * @param node Child node
     * @param children Child iterable
     * @param mixinParent {@code true} if parent is mixin, {@code false} otherwise
     * @return True
     * @throws IOException when the writer reports it
     */
    protected final boolean writeChildren(final NormalizedNode<?, ?> node,
            final Iterable<? extends NormalizedNode<?, ?>> children, final boolean mixinParent) throws IOException {
        boolean writeAllChildren = writeAllChildren(children);
        for (final NormalizedNode<?, ?> child : children) {
            if (writeAllChildren) {
                write(child);
            } else {
                parentNode = node.getIdentifier().getNodeType().getLocalName();
                if (child instanceof ContainerNode == true || child instanceof AugmentationNode == true) {
                    if (selectedByParameters(child, mixinParent)) {
                        write(child);
                    }
                } else {
                    if (checkParentChildRelation(parentNode, child.getIdentifier().getNodeType().getLocalName())) {
                        if (selectedByParameters(child, mixinParent)) {
                            write(child);
                        }
                    }
                }
            }
        }
        writer.endNode();
        return true;
    }

    protected boolean writeMapEntryChildren(final MapEntryNode mapEntryNode) throws IOException {
        if (selectedByParameters(mapEntryNode, false)) {
            writeChildren(mapEntryNode, mapEntryNode.getValue(), false);
        } else if (fields == null && maxDepth != null && currentDepth == maxDepth) {
            writeOnlyKeys(mapEntryNode.getIdentifier().entrySet());
        }
        return true;
    }

    private void writeOnlyKeys(final Set<Entry<QName, Object>> entries) throws IOException {
        for (final Entry<QName, Object> entry : entries) {
            writer.startLeafNode(new NodeIdentifier(entry.getKey()));
            writer.scalarValue(entry.getValue());
            writer.endNode();
        }
        writer.endNode();
    }

    protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
        writer.startMapEntryNode(node.getIdentifier(), childSizeHint(node.getValue()));
        currentDepth++;
        writeMapEntryChildren(node);
        currentDepth--;
        return true;
    }

    private boolean wasProcessedAsCompositeNode(final NormalizedNode<?, ?> node) throws IOException {
        boolean processedAsCompositeNode = false;
        if (node instanceof ContainerNode) {
            final ContainerNode n = (ContainerNode) node;
            if (!n.getNodeType().withoutRevision().equals(ROOT_DATA_QNAME)) {
                writer.startContainerNode(n.getIdentifier(), childSizeHint(n.getValue()));
                currentDepth++;
                processedAsCompositeNode = writeChildren(n, n.getValue(), false);
                currentDepth--;
            } else {
                // write child nodes of data root container
                for (final NormalizedNode<?, ?> child : n.getValue()) {
                    currentDepth++;
                    if (selectedByParameters(child, false)) {
                        write(child);
                    }
                    currentDepth--;
                    processedAsCompositeNode = true;
                }
            }
        } else if (node instanceof MapEntryNode) {
            processedAsCompositeNode = writeMapEntryNode((MapEntryNode) node);
        } else if (node instanceof UnkeyedListEntryNode) {
            final UnkeyedListEntryNode n = (UnkeyedListEntryNode) node;
            writer.startUnkeyedListItem(n.getIdentifier(), childSizeHint(n.getValue()));
            currentDepth++;
            processedAsCompositeNode = writeChildren(n, n.getValue(), false);
            currentDepth--;
        } else if (node instanceof ChoiceNode) {
            final ChoiceNode n = (ChoiceNode) node;
            writer.startChoiceNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n, n.getValue(), true);
        } else if (node instanceof AugmentationNode) {
            final AugmentationNode n = (AugmentationNode) node;
            writer.startAugmentationNode(n.getIdentifier());
            processedAsCompositeNode = writeChildren(n, n.getValue(), true);
        } else if (node instanceof UnkeyedListNode) {
            final UnkeyedListNode n = (UnkeyedListNode) node;
            writer.startUnkeyedList(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n, n.getValue(), false);
        } else if (node instanceof OrderedMapNode) {
            final OrderedMapNode n = (OrderedMapNode) node;
            writer.startOrderedMapNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n, n.getValue(), true);
        } else if (node instanceof MapNode) {
            final MapNode n = (MapNode) node;
            writer.startMapNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n, n.getValue(), true);
        } else if (node instanceof LeafSetNode) {
            final LeafSetNode<?> n = (LeafSetNode<?>) node;
            if (node instanceof OrderedLeafSetNode) {
                writer.startOrderedLeafSet(n.getIdentifier(), childSizeHint(n.getValue()));
            } else {
                writer.startLeafSet(n.getIdentifier(), childSizeHint(n.getValue()));
            }
            currentDepth++;
            processedAsCompositeNode = writeChildren(n, n.getValue(), true);
            currentDepth--;
        }

        return processedAsCompositeNode;
    }

    private static final class OrderedParameterAwareNormalizedNodeWriter extends ParameterAwareNormalizedNodeWriter {
        private static final Logger LOG = LoggerFactory.getLogger(OrderedParameterAwareNormalizedNodeWriter.class);

        OrderedParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final Integer maxDepth,
                                                  final List<Set<QName>> fields,
                                                  final List<String> parentChildRelation) {
            super(writer, maxDepth, fields, parentChildRelation);
        }

        /**
         * check child node exists in parameters field list or not.
         *
         * @param children Child node
         * @return {@code true} if child exists in parameters field list, {@code false} otherwise
         */
        protected boolean doesNodeExistInSearchFields(final NormalizedNode<?, ?> node) {
            if (fields != null) {
                for (Set<QName> field : fields) {
                    if (field.contains(node.getNodeType())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
            final NormalizedNodeStreamWriter writer = getWriter();
            writer.startMapEntryNode(node.getIdentifier(), childSizeHint(node.getValue()));

            final Set<QName> qnames = node.getIdentifier().keySet();
            // Write out all the key children
            currentDepth++;
            for (final QName qname : qnames) {
                final Optional<? extends NormalizedNode<?, ?>> child = node.getChild(new NodeIdentifier(qname));
                if (child.isPresent()) {
                    if (doesNodeExistInSearchFields(child.get())) {
                        if (selectedByParameters(child.get(), false)) {
                            write(child.get());
                        }
                    }
                    else {
                        write(child.get());
                    }
                } else {
                    LOG.info("No child for key element {} found", qname);
                }
            }
            currentDepth--;

            currentDepth++;
            // Write all the rest
            final boolean result =
                    writeChildren(node, Iterables.filter(node.getValue(), input -> {
                        if (input instanceof AugmentationNode) {
                            return true;
                        }
                        if (!qnames.contains(input.getNodeType())) {
                            return true;
                        }

                        LOG.debug("Skipping key child {}", input);
                        return false;
                    }), false);
            currentDepth--;
            return result;
        }
    }
}

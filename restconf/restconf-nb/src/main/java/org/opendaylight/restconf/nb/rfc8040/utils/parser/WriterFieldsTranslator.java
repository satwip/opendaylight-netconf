/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.ParameterAwareNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;

/**
 * Fields parser that stores set of {@link QName}s in each level. Because of this fact, from the output
 * it is is only possible to assume on what depth the selected element is placed. Identifiers of intermediary
 * mixin nodes are also flatten to the same level as identifiers of data nodes.<br>
 * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
 * <pre>
 * level 0: ['a', 'd']
 * level 1: ['b', 'x', 'e']
 * level 2: ['c']
 * </pre>
 */
public final class WriterFieldsTranslator extends AbstractFieldsTranslator<QName> {
    private static final WriterFieldsTranslator INSTANCE = new WriterFieldsTranslator();

    private WriterFieldsTranslator() {
        // Hidden on purpose
    }

    /**
     * Translate a {@link FieldsParam} to a complete list of child nodes organized into levels, suitable for use with
     * {@link ParameterAwareNormalizedNodeWriter}.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains set of {@link QName}
     */
    public static @NonNull List<Set<QName>> translate(final @NonNull InstanceIdentifierContext identifier,
                                                      final @NonNull FieldsParam input) {
        return INSTANCE.parseFields(identifier, input);
    }

    @Override
    protected DataSchemaContext addChildToResult(final DataSchemaContext currentNode, final QName childQName,
            final Set<QName> level) {
        // resolve parent node
        final DataSchemaContext parentNode = resolveMixinNode(currentNode, level,
            currentNode.getPathStep().getNodeType());
        if (parentNode == null) {
            throw new RestconfDocumentedException(
                    "Not-mixin node missing in " + currentNode.getPathStep().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // resolve child node
        final DataSchemaContext childNode = resolveMixinNode(childByQName(parentNode, childQName), level, childQName);
        if (childNode == null) {
            throw new RestconfDocumentedException(
                    "Child " + childQName.getLocalName() + " node missing in "
                            + currentNode.getPathStep().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // add final childNode node to level nodes
        level.add(childNode.getPathStep().getNodeType());
        return childNode;
    }

    private static @Nullable DataSchemaContext childByQName(final DataSchemaContext parent, final QName qname) {
        return parent instanceof DataSchemaContext.Composite composite ? composite.childByQName(qname) : null;
    }

    /**
     * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
     * All nodes expect of not mixin node are added to current level nodes.
     *
     * @param node          initial mixin or not-mixin node
     * @param level         current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    private static @Nullable DataSchemaContext resolveMixinNode(final @Nullable DataSchemaContext node,
            final @NonNull Set<QName> level, final @NonNull QName qualifiedName) {
        DataSchemaContext currentNode = node;
        while (currentNode != null && currentNode instanceof PathMixin currentMixin) {
            level.add(qualifiedName);
            currentNode = currentMixin.childByQName(qualifiedName);
        }

        return currentNode;
    }
}
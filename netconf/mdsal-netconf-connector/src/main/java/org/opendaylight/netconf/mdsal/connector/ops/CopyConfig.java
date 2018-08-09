/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class CopyConfig extends AbstractEdit {
    private static final String OPERATION_NAME = "copy-config";
    private static final String SOURCE_KEY = "source";

    // Top-level "data" node without child nodes
    private static final ContainerNode EMPTY_ROOT_NODE = Builders.containerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SchemaContext.NAME)).build();

    private final TransactionProvider transactionProvider;

    public CopyConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                      final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Datastore targetDatastore = extractTargetParameter(operationElement, OPERATION_NAME);
        if (targetDatastore == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_NOT_SUPPORTED,
                    ErrorSeverity.ERROR);
        }
        final XmlElement source = getSourceElement(operationElement);
        final List<XmlElement> configElements = getConfigElement(source).getChildElements();

        // <copy-config>, unlike <edit-config>, always replaces entire configuration,
        // so remove old configuration first:
        final DOMDataReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        rwTx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY, EMPTY_ROOT_NODE);

        // Then create nodes present in the <config> element:
        for (final XmlElement element : configElements) {
            final String ns = element.getNamespace();
            final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element);
            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            parseIntoNormalizedNode(schemaNode, element, ImmutableNormalizedNodeStreamWriter.from(resultHolder));
            final NormalizedNode<?, ?> data = resultHolder.getResult();
            final YangInstanceIdentifier path = YangInstanceIdentifier.create(data.getIdentifier());
            // Doing merge instead of put to support top-level list:
            rwTx.merge(LogicalDatastoreType.CONFIGURATION, path, data);
        }
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

    private static XmlElement getSourceElement(final XmlElement parent) throws DocumentedException {
        final Optional<XmlElement> sourceElement = parent.getOnlyChildElementOptionally(SOURCE_KEY);
        if (!sourceElement.isPresent()) {
            throw new DocumentedException("<source> element is missing",
                DocumentedException.ErrorType.PROTOCOL,
                DocumentedException.ErrorTag.MISSING_ELEMENT,
                DocumentedException.ErrorSeverity.ERROR);
        }

        return sourceElement.get();
    }
}

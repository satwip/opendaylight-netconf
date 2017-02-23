/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import com.google.common.base.Preconditions;
import java.util.Collection;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaContextHandler}
 *
 */
public class SchemaContextHandler implements SchemaContextListenerHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    private final TransactionChainHandler transactionChainHandler;
    private SchemaContext context;

    private int moduleSetId;

    /**
     * Set module-set-id on initial value - 0
     *
     * @param transactionChainHandler
     */
    public SchemaContextHandler(final TransactionChainHandler transactionChainHandler) {
        this.transactionChainHandler = transactionChainHandler;
        this.moduleSetId = 0;
    }

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        Preconditions.checkNotNull(context);
        this.context = null;
        this.context = context;
        this.moduleSetId++;
        final Module ietfYangLibraryModule =
                context.findModuleByNamespaceAndRevision(IetfYangLibrary.URI_MODULE, IetfYangLibrary.DATE);
        NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(context.getModules(), ietfYangLibraryModule,
                        context, String.valueOf(this.moduleSetId));
        putData(normNode, 2);

        final Module monitoringModule =
                this.context.findModuleByNamespaceAndRevision(MonitoringModule.URI_MODULE, MonitoringModule.DATE);
        normNode = RestconfMappingNodeUtil.mapCapabilites(monitoringModule);
        putData(normNode, 2);
    }

    @Override
    public SchemaContext get() {
        return this.context;
    }

    private void putData(
            final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode,
            final int tries) {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.create(NodeIdentifier.create(normNode.getNodeType())), normNode);
        try {
            wTx.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            final Throwable cause = e.getCause();
            if ((cause.getCause() instanceof OptimisticLockFailedException) && (tries - 1 > 0)) {
                /*
                  Ignore error when another cluster node is already putting the same data to DS.
                  And try to put the same data again for specified number of tries.
                  This is workaround for bug:
                  https://bugs.opendaylight.org/show_bug.cgi?id=7728
                */
                LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);

                // reset transaction chain and try to put data again
                RestConnectorProvider.resetTransactionChainForAdapaters(this.transactionChainHandler.get());
                putData(normNode, tries - 1);
            } else {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
            }
        }
    }
}

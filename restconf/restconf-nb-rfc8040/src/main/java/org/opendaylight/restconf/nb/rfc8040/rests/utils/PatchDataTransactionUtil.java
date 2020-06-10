/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PatchData;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PatchDataTransactionUtil.class);

    private PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Process edit operations of one {@link PatchContext}. Close {@link DOMTransactionChain} inside of object
     * {@link TransactionVarsWrapper} provided as a parameter.
     *
     * @param context          Patch context to be processed
     * @param transactionNode  Wrapper for transaction
     * @param schemaContextRef Soft reference for global schema context
     * @return {@link PatchStatusContext}
     */
    public static PatchStatusContext patchData(final PatchContext context, final TransactionVarsWrapper transactionNode,
                                               final SchemaContextRef schemaContextRef) {
        final List<PatchStatusEntity> editCollection = new ArrayList<>();
        boolean noError = true;
        final NetconfDataTreeService netconfService = transactionNode.getNetconfDataTreeService();
        final DOMTransactionChain transactionChain = transactionNode.getTransactionChain();
        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures;
        final DOMDataTreeReadWriteTransaction tx;
        if (netconfService != null) {
            resultsFutures = netconfService.lock();
            tx = null;
        } else {
            resultsFutures = null;
            tx = transactionChain.newReadWriteTransaction();
        }

        for (final PatchEntity patchEntity : context.getData()) {
            if (noError) {
                switch (patchEntity.getOperation()) {
                    case CREATE:
                        try {
                            createDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), tx, schemaContextRef,
                                    transactionNode, resultsFutures);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case DELETE:
                        try {
                            deleteDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    tx, transactionNode, resultsFutures);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case MERGE:
                        try {
                            mergeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), tx, schemaContextRef,
                                    transactionNode, resultsFutures);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REPLACE:
                        try {
                            replaceDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), schemaContextRef, tx,
                                    transactionNode, resultsFutures);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REMOVE:
                        try {
                            removeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    tx, transactionNode, resultsFutures);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                false, Lists.newArrayList(new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.OPERATION_NOT_SUPPORTED, "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // if no errors then submit transaction, otherwise cancel
        if (noError) {
            final ResponseFactory response = new ResponseFactory(Status.OK);
            final FluentFuture<? extends CommitInfo> future;
            if (netconfService != null) {
                future = FluentFuture.from(netconfService.commit(resultsFutures));
            } else {
                future = tx.commit();
            }

            try {
                if (netconfService != null) {
                    FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);
                } else {
                    //This method will close transactionChain
                    FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response, transactionChain);
                }
            } catch (final RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false,
                        Lists.newArrayList(e.getErrors()));
            }

            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                    true, null);
        } else {
            if (netconfService != null) {
                netconfService.discardChanges();
                netconfService.unlock();
            } else {
                tx.cancel();
                transactionChain.close();
            }
            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                    false, null);
        }
    }

    /**
     * Create data within one transaction, return error if already exists.
     *
     * @param dataStore        Datastore to write data to
     * @param path             Path for data to be created
     * @param payload          Data to be created
     * @param rwTransaction    Transaction
     * @param schemaContextRef Soft reference for global schema context
     */
    private static void createDataWithinTransaction(
            final LogicalDatastoreType dataStore,
            final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> payload,
            final DOMDataTreeReadWriteTransaction rwTransaction,
            final SchemaContextRef schemaContextRef,
            final TransactionVarsWrapper varsWrapper,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContextRef.get(), path, rwTransaction, dataStore, true, varsWrapper,
                resultsFutures);
    }

    /**
     * Check if data exists and remove it within one transaction.
     *
     * @param dataStore            Datastore to delete data from
     * @param path                 Path for data to be deleted
     * @param readWriteTransaction Transaction
     */
    private static void deleteDataWithinTransaction(
            final LogicalDatastoreType dataStore,
            final YangInstanceIdentifier path,
            final DOMDataTreeReadWriteTransaction readWriteTransaction,
            final TransactionVarsWrapper varsWrapper,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.trace("Delete {} within Restconf Patch: {}", dataStore.name(), path);
        final NetconfDataTreeService netconfService = varsWrapper.getNetconfDataTreeService();
        if (netconfService != null) {
            checkItemExistsWithinTransaction(netconfService, dataStore, path);
            resultsFutures.add(netconfService.delete(dataStore, path));
        } else {
            checkItemExistsWithinTransaction(readWriteTransaction, dataStore, path);
            readWriteTransaction.delete(dataStore, path);
        }
    }

    /**
     * Merge data within one transaction.
     *
     * @param dataStore        Datastore to merge data to
     * @param path             Path for data to be merged
     * @param payload          Data to be merged
     * @param writeTransaction Transaction
     * @param schemaContextRef Soft reference for global schema context
     */
    private static void mergeDataWithinTransaction(
            final LogicalDatastoreType dataStore,
            final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> payload,
            final DOMDataTreeWriteTransaction writeTransaction,
            final SchemaContextRef schemaContextRef,
            final TransactionVarsWrapper varsWrapper,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        final NetconfDataTreeService netconfService = varsWrapper.getNetconfDataTreeService();
        if (netconfService != null) {
            TransactionUtil.ensureParentsByMerge(path, schemaContextRef.get(), netconfService, resultsFutures);
            resultsFutures.add(netconfService.merge(dataStore, path, payload, Optional.empty()));
        } else {
            TransactionUtil.ensureParentsByMerge(path, schemaContextRef.get(), writeTransaction);
            writeTransaction.merge(dataStore, path, payload);
        }
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     *
     * @param dataStore        Datastore to delete data from
     * @param path             Path for data to be deleted
     * @param writeTransaction Transaction
     */
    private static void removeDataWithinTransaction(
            final LogicalDatastoreType dataStore,
            final YangInstanceIdentifier path,
            final DOMDataTreeWriteTransaction writeTransaction,
            final TransactionVarsWrapper varsWrapper,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.trace("Remove {} within Restconf Patch: {}", dataStore.name(), path);
        final NetconfDataTreeService netconfService = varsWrapper.getNetconfDataTreeService();
        if (netconfService != null) {
            resultsFutures.add(netconfService.delete(dataStore, path));
        } else {
            writeTransaction.delete(dataStore, path);
        }
    }

    /**
     * Create data within one transaction, replace if already exists.
     *
     * @param dataStore        Datastore to write data to
     * @param path             Path for data to be created
     * @param payload          Data to be created
     * @param schemaContextRef Soft reference for global schema context
     * @param rwTransaction    Transaction
     */
    private static void replaceDataWithinTransaction(
            final LogicalDatastoreType dataStore,
            final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> payload,
            final SchemaContextRef schemaContextRef,
            final DOMDataTreeReadWriteTransaction rwTransaction,
            final TransactionVarsWrapper varsWrapper,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.trace("PUT {} within Restconf Patch: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContextRef.get(), path, rwTransaction, dataStore, false, varsWrapper,
                resultsFutures);
    }

    /**
     * Create data within one transaction. If {@code errorIfExists} is set to {@code true} then data will be checked
     * for existence before created, otherwise they will be overwritten.
     *
     * @param payload       Data to be created
     * @param schemaContext Global schema context
     * @param path          Path for data to be created
     * @param rwTransaction Transaction
     * @param dataStore     Datastore to write data to
     * @param errorIfExists Enable checking for existence of data (throws error if already exists)
     */
    private static void createData(final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
                                   final YangInstanceIdentifier path,
                                   final DOMDataTreeReadWriteTransaction rwTransaction,
                                   final LogicalDatastoreType dataStore, final boolean errorIfExists,
                                   final TransactionVarsWrapper varsWrapper,
                                   final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final NetconfDataTreeService netconfService = varsWrapper.getNetconfDataTreeService();

        if (payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            if (netconfService != null) {
                resultsFutures.add(
                        netconfService.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()),
                                emptySubtree, Optional.empty()));
                TransactionUtil.ensureParentsByMerge(path, schemaContext, netconfService, resultsFutures);
            } else {
                rwTransaction.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()),
                        emptySubtree);
                TransactionUtil.ensureParentsByMerge(path, schemaContext, rwTransaction);
            }
            for (final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());

                if (errorIfExists) {
                    if (netconfService != null) {
                        checkItemDoesNotExistsWithinTransaction(netconfService, dataStore, childPath);
                    } else {
                        checkItemDoesNotExistsWithinTransaction(rwTransaction, dataStore, childPath);
                    }
                }

                if (netconfService != null) {
                    if (errorIfExists) {
                        resultsFutures.add(netconfService.create(dataStore, childPath, child, Optional.empty()));
                    } else {
                        resultsFutures.add(netconfService.replace(dataStore, childPath, child, Optional.empty()));
                    }
                } else {
                    rwTransaction.put(dataStore, childPath, child);
                }
            }
        } else {
            if (errorIfExists) {
                if (netconfService != null) {
                    checkItemDoesNotExistsWithinTransaction(netconfService, dataStore, path);
                } else {
                    checkItemDoesNotExistsWithinTransaction(rwTransaction, dataStore, path);
                }
            }

            if (netconfService != null) {
                TransactionUtil.ensureParentsByMerge(path, schemaContext, netconfService, resultsFutures);
                if (errorIfExists) {
                    resultsFutures.add(netconfService.create(dataStore, path, payload, Optional.empty()));
                } else {
                    resultsFutures.add(netconfService.replace(dataStore, path, payload, Optional.empty()));
                }
            } else {
                TransactionUtil.ensureParentsByMerge(path, schemaContext, rwTransaction);
                rwTransaction.put(dataStore, path, payload);
            }
        }
    }

    /**
     * Check if items already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data does NOT already exists.
     *
     * @param rwTransaction Transaction
     * @param store         Datastore
     * @param path          Path to be checked
     */
    public static void checkItemExistsWithinTransaction(final DOMDataTreeReadOperations rwTransaction,
                                                        final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = rwTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (!response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    path);
        }
    }

    public static void checkItemExistsWithinTransaction(final NetconfDataTreeService netconfService,
                                                        final LogicalDatastoreType store,
                                                        final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = TransactionUtil.isDataExists(netconfService, store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (!response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    path);
        }
    }

    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     *
     * @param rwTransaction Transaction
     * @param store         Datastore
     * @param path          Path to be checked
     */
    public static void checkItemDoesNotExistsWithinTransaction(final DOMDataTreeReadWriteTransaction rwTransaction,
                                                               final LogicalDatastoreType store,
                                                               final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = rwTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                    path);
        }
    }

    public static void checkItemDoesNotExistsWithinTransaction(final NetconfDataTreeService netconfService,
                                                               final LogicalDatastoreType store,
                                                               final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = TransactionUtil.isDataExists(netconfService, store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                    path);
        }
    }
}

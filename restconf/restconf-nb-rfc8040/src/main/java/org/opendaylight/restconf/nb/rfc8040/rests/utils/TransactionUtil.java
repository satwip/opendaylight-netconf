/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for common methods of transactions.
 *
 */
public final class TransactionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionUtil.class);

    private TransactionUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Merged parents of data.
     *
     * @param path
     *             path of data
     * @param schemaContext
     *             {@link SchemaContext}
     * @param writeTx
     *             write transaction
     */
    public static void ensureParentsByMerge(final YangInstanceIdentifier path, final SchemaContext schemaContext,
            final DOMDataTreeWriteTransaction writeTx) {
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        Preconditions.checkArgument(rootNormalizedPath != null, "Empty path received");

        final NormalizedNode<?, ?> parentStructure = ImmutableNodes.fromInstanceId(schemaContext,
                YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, rootNormalizedPath, parentStructure);
    }

    public static void ensureParentsByMerge(final YangInstanceIdentifier path, final SchemaContext schemaContext,
                                            final NetconfDataTreeService netconfService,
                                            List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        final NormalizedNode<?, ?> parentStructure = ImmutableNodes.fromInstanceId(schemaContext,
                YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        resultsFutures.add(
                netconfService.merge(LogicalDatastoreType.CONFIGURATION, rootNormalizedPath,
                        parentStructure, Optional.empty()));
    }

    /**
     * Check if items already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data does NOT already exists.
     * @param transactionChain Transaction chain
     * @param rwTransaction Transaction
     * @param store Datastore
     * @param path Path to be checked
     * @param operationType Type of operation (READ, POST, PUT, DELETE...)
     */
    public static void checkItemExists(final DOMTransactionChain transactionChain,
                                       final DOMDataTreeReadWriteTransaction rwTransaction,
                                       final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                       final String operationType) {
        final FluentFuture<Boolean> future = rwTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, operationType, response);

        if (!response.result) {
            // close transaction
            rwTransaction.cancel();
            transactionChain.close();
            // throw error
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException(
                    "Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, path);
        }
    }

    public static void checkItemExists(final NetconfDataTreeService netconfService,
                                       final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                       final String operationType) {
        final FluentFuture<Boolean> future = isDataExists(netconfService, store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, operationType, response);

        if (!response.result) {
            // close transaction
            netconfService.discardChanges();
            netconfService.unlock();
            // throw error
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException(
                    "Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, path);
        }
    }

    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     * @param transactionChain Transaction chain
     * @param rwTransaction Transaction
     * @param store Datastore
     * @param path Path to be checked
     * @param operationType Type of operation (READ, POST, PUT, DELETE...)
     */
    public static void checkItemDoesNotExists(final DOMTransactionChain transactionChain,
                                              final DOMDataTreeReadWriteTransaction rwTransaction,
                                              final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                              final String operationType) {
        final FluentFuture<Boolean> future = rwTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, operationType, response);

        if (response.result) {
            // close transaction
            rwTransaction.cancel();
            transactionChain.close();
            // throw error
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException(
                    "Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, path);
        }
    }

    public static void checkItemDoesNotExists(final NetconfDataTreeService netconfService,
                                              final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                              final String operationType) {
        final FluentFuture<Boolean> future = isDataExists(netconfService, store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, operationType, response);

        if (response.result) {
            netconfService.discardChanges();
            netconfService.unlock();
            // throw error
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException(
                    "Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, path);
        }
    }

    public static FluentFuture<Boolean> isDataExists(final NetconfDataTreeService netconfDataTreeService,
                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return remapException(netconfDataTreeService.readData(store, path))
                .transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                MoreExecutors.directExecutor());
    }

    private static <T> FluentFuture<T> remapException(final ListenableFuture<T> input) {
        final SettableFuture<T> ret = SettableFuture.create();
        Futures.addCallback(input, new FutureCallback<T>() {

            @Override
            public void onSuccess(final T result) {
                ret.set(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(cause instanceof ReadFailedException ? cause
                        : new ReadFailedException("NETCONF operation failed", cause));
            }
        }, MoreExecutors.directExecutor());
        return FluentFuture.from(ret);
    }
}

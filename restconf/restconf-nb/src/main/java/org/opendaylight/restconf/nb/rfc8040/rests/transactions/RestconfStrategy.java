/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.TransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierDeserializer;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline execution strategy for various RESTCONF operations.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
// FIXME: it seems the first three operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
public abstract class RestconfStrategy {
    /**
     * Result of a {@code PUT} request as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>. The definition makes it
     * clear that the logical operation is {@code create-or-replace}.
     */
    public enum CreateOrReplaceResult {
        /**
         * A new resource has been created.
         */
        CREATED,
        /*
         * An existing resources has been replaced.
         */
        REPLACED;
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStrategy.class);

    RestconfStrategy() {
        // Hidden on purpose
    }

    /**
     * Look up the appropriate strategy for a particular mount point.
     *
     * @param mountPoint Target mount point
     * @return A strategy, or null if the mount point does not expose a supported interface
     * @throws NullPointerException if {@code mountPoint} is null
     */
    public static Optional<RestconfStrategy> forMountPoint(final DOMMountPoint mountPoint) {
        final Optional<RestconfStrategy> netconf = mountPoint.getService(NetconfDataTreeService.class)
            .map(NetconfRestconfStrategy::new);
        if (netconf.isPresent()) {
            return netconf;
        }

        return mountPoint.getService(DOMDataBroker.class).map(MdsalRestconfStrategy::new);
    }

    /**
     * Lock the entire datastore.
     *
     * @return A {@link RestconfTransaction}. This transaction needs to be either committed or canceled before doing
     *         anything else.
     */
    public abstract RestconfTransaction prepareWriteExecution();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    public abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store,
        YangInstanceIdentifier path);

    /**
     * Read data selected using fields from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the parent data object path
     * @param fields paths to selected fields relative to parent path
     * @return a ListenableFuture containing the result of the read
     */
    public abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store,
            YangInstanceIdentifier path, List<YangInstanceIdentifier> fields);

    /**
     * Check if data already exists in the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a FluentFuture containing the result of the check
     */
    abstract ListenableFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Delete data from the configuration datastore. If the data does not exist, this operation will fail, as outlined
     * in <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.7">RFC8040 section 4.7</a>
     *
     * @param path Path to delete
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> delete(final YangInstanceIdentifier path) {
        final var ret = new SettableRestconfFuture<Empty>();
        delete(ret, requireNonNull(path));
        return ret;
    }

    protected abstract void delete(@NonNull SettableRestconfFuture<Empty> future, @NonNull YangInstanceIdentifier path);

    /**
     * Merge data into the configuration datastore, as outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 section 4.6.1</a>.
     *
     * @param path Path to merge
     * @param data Data to merge
     * @param context Corresponding EffectiveModelContext
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if any argument is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> merge(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext context) {
        final var ret = new SettableRestconfFuture<Empty>();
        merge(ret, requireNonNull(path), requireNonNull(data), requireNonNull(context));
        return ret;
    }

    private void merge(final @NonNull SettableRestconfFuture<Empty> future,
            final @NonNull YangInstanceIdentifier path, final @NonNull NormalizedNode data,
            final @NonNull EffectiveModelContext context) {
        final var tx = prepareWriteExecution();
        // FIXME: this method should be further specialized to eliminate this call -- it is only needed for MD-SAL
        TransactionUtil.ensureParentsByMerge(path, context, tx);
        tx.merge(path, data);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                future.set(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setFailure(TransactionUtil.decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Check mount point and prepare variables for put data to DS. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param path    path of data
     * @param data    data
     * @param context reference to {@link EffectiveModelContext}
     * @param params  {@link WriteDataParams}
     * @return A {@link CreateOrReplaceResult}
     */
    public @NonNull CreateOrReplaceResult putData(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext context, final WriteDataParams params) {
        final var exists = TransactionUtil.syncAccess(exists(LogicalDatastoreType.CONFIGURATION, path), path);

        final var insert = params.insert();
        final ListenableFuture<? extends CommitInfo> commitFuture;
        if (insert != null) {
            final var parentPath = path.coerceParent();
            checkListAndOrderedType(context, parentPath);
            commitFuture = insertAndCommit(path, data, insert, params.point(), parentPath, context);
        } else {
            commitFuture = replaceAndCommit(prepareWriteExecution(), path, data, context);
        }

        TransactionUtil.syncCommit(commitFuture, "PUT", path);
        return exists ? CreateOrReplaceResult.REPLACED : CreateOrReplaceResult.CREATED;
    }

    private ListenableFuture<? extends CommitInfo> insertAndCommit(final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull InsertParam insert, final @Nullable PointParam point,
            final YangInstanceIdentifier parentPath, final EffectiveModelContext context) {
        final var tx = prepareWriteExecution();

        return switch (insert) {
            case FIRST -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data, context);
                }
                tx.remove(parentPath);
                tx.replace(path, data, context);
                tx.replace(parentPath, readData, context);
                yield tx.commit();
            }
            case LAST -> replaceAndCommit(tx, path, data, context);
            case BEFORE -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data, context);
                }
                insertWithPointPut(tx, path, data, verifyNotNull(point), readData, true, context);
                yield tx.commit();
            }
            case AFTER -> {
                final var readData = tx.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield replaceAndCommit(tx, path, data, context);
                }
                insertWithPointPut(tx, path, data, verifyNotNull(point), readData, false, context);
                yield tx.commit();
            }
        };
    }

    private static void insertWithPointPut(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode data, final @NonNull PointParam point, final NormalizedNodeContainer<?> readList,
            final boolean before, final EffectiveModelContext context) {
        tx.remove(path.getParent());
        final var pointArg = YangInstanceIdentifierDeserializer.create(context, point.value()).path
            .getLastPathArgument();
        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final var emptySubtree = ImmutableNodes.fromInstanceId(context, path.getParent());
        tx.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                tx.replace(path, data, context);
            }
            final var childPath = path.coerceParent().node(nodeChild.name());
            tx.replace(childPath, nodeChild, context);
            lastInsertedPosition++;
        }
    }

    private static ListenableFuture<? extends CommitInfo> replaceAndCommit(final RestconfTransaction tx,
            final YangInstanceIdentifier path, final NormalizedNode data, final EffectiveModelContext context) {
        tx.replace(path, data, context);
        return tx.commit();
    }

    /**
     * Check mount point and prepare variables for post data. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param path          path
     * @param data          data
     * @param schemaContext reference to actual {@link EffectiveModelContext}
     * @param params        {@link WriteDataParams}
     */
    public void postData(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext schemaContext, final WriteDataParams params) {
        TransactionUtil.syncCommit(submitData(path, data, schemaContext, params), "POST", path);
    }

    private static DataSchemaNode checkListAndOrderedType(final EffectiveModelContext ctx,
            final YangInstanceIdentifier path) {
        final var dataSchemaNode = DataSchemaContextTree.from(ctx).findChild(path).orElseThrow().dataSchemaNode();

        final String message;
        if (dataSchemaNode instanceof ListSchemaNode listSchema) {
            if (listSchema.isUserOrdered()) {
                return listSchema;
            }
            message = "Insert parameter can be used only with ordered-by user list.";
        } else if (dataSchemaNode instanceof LeafListSchemaNode leafListSchema) {
            if (leafListSchema.isUserOrdered()) {
                return leafListSchema;
            }
            message = "Insert parameter can be used only with ordered-by user leaf-list.";
        } else {
            message = "Insert parameter can be used only with list or leaf-list";
        }
        throw new RestconfDocumentedException(message, ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
    }

    /**
     * Post data by type.
     *
     * @param path          path
     * @param data          data
     * @param schemaContext schema context of data
     * @param point         query parameter
     * @param insert        query parameter
     * @return {@link FluentFuture}
     */
    private ListenableFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
            final NormalizedNode data, final EffectiveModelContext schemaContext, final WriteDataParams params) {
        final var transaction = prepareWriteExecution();
        final var insert = params.insert();
        if (insert == null) {
            return makePost(path, data, schemaContext, transaction);
        }

        final var parentPath = path.coerceParent();
        checkListAndOrderedType(schemaContext, parentPath);
        final var grandParentPath = parentPath.coerceParent();

        return switch (insert) {
            case FIRST -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    transaction.remove(grandParentPath);
                    transaction.replace(path, data, schemaContext);
                    transaction.replace(grandParentPath, readData, schemaContext);
                }
                yield transaction.commit();
            }
            case LAST -> makePost(path, data, schemaContext, transaction);
            case BEFORE -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    insertWithPointPost(path, data, schemaContext, params.getPoint(), readData, true, transaction);
                }
                yield transaction.commit();
            }
            case AFTER -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    insertWithPointPost(path, data, schemaContext, params.getPoint(), readData, false, transaction);
                }
                yield transaction.commit();
            }
        };
    }

    private static void insertWithPointPost(final YangInstanceIdentifier path, final NormalizedNode data,
                                            final EffectiveModelContext schemaContext, final PointParam point,
                                            final NormalizedNodeContainer<?> readList, final boolean before,
                                            final RestconfTransaction transaction) {
        final YangInstanceIdentifier parent = path.coerceParent().coerceParent();
        transaction.remove(parent);
        final var pointArg = YangInstanceIdentifierDeserializer.create(schemaContext, point.value()).path
            .getLastPathArgument();
        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final var emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, parent);
        transaction.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = parent.node(nodeChild.name());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static ListenableFuture<? extends CommitInfo> makePost(final YangInstanceIdentifier path,
            final NormalizedNode data, final EffectiveModelContext schemaContext,
            final RestconfTransaction transaction) {
        try {
            transaction.create(path, data, schemaContext);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            transaction.cancel();
            throw e;
        }

        return transaction.commit();
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @param path         Path to be checked
     * @throws RestconfDocumentedException if data already exists.
     */
    static void checkItemDoesNotExists(final ListenableFuture<Boolean> existsFuture,
            final YangInstanceIdentifier path) {
        if (TransactionUtil.syncAccess(existsFuture, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                path);
        }
    }
}

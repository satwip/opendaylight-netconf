/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker.ConsumerSession;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerFacade {
    private final static Logger LOG = LoggerFactory.getLogger(BrokerFacade.class);

    private final static BrokerFacade INSTANCE = new BrokerFacade();
    private volatile DOMRpcService rpcService;
    private volatile ConsumerSession context;
    private DOMDataBroker domDataBroker;

    private BrokerFacade() {
    }

    public void setRpcService(final DOMRpcService router) {
        rpcService = router;
    }

    public void setContext(final ConsumerSession context) {
        this.context = context;
    }

    public static BrokerFacade getInstance() {
        return BrokerFacade.INSTANCE;
    }

    private void checkPreconditions() {
        if (context == null || domDataBroker == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
    }

    // READ configuration
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path) {
        checkPreconditions();
        return readDataViaTransaction(domDataBroker.newReadOnlyTransaction(), CONFIGURATION, path);
    }

    public NormalizedNode<?, ?> readConfigurationData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return readDataViaTransaction(domDataBrokerService.get().newReadOnlyTransaction(), CONFIGURATION, path);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // READ operational
    public NormalizedNode<?, ?> readOperationalData(final YangInstanceIdentifier path) {
        checkPreconditions();
        return readDataViaTransaction(domDataBroker.newReadOnlyTransaction(), OPERATIONAL, path);
    }

    public NormalizedNode<?, ?> readOperationalData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return readDataViaTransaction(domDataBrokerService.get().newReadOnlyTransaction(), OPERATIONAL, path);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // PUT configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPut(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        checkPreconditions();
        return putDataViaTransaction(domDataBroker, CONFIGURATION, path, payload, globalSchema);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPut(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return putDataViaTransaction(domDataBrokerService.get(), CONFIGURATION, path,
                    payload, mountPoint.getSchemaContext());
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    public PATCHStatusContext patchConfigurationDataWithinTransaction(final PATCHContext context,
                                                                      final SchemaContext globalSchema)
            throws TransactionCommitFailedException {
        final DOMDataReadWriteTransaction patchTransaction = domDataBroker.newReadWriteTransaction();
        List<PATCHStatusEntity> editCollection = new ArrayList<>();
        List<RestconfError> editErrors;
        List<RestconfError> globalErrors = null;
        int errorCounter = 0;

        for (PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation().toUpperCase());

            switch (operation) {
                case CREATE:
                    if (errorCounter == 0) {
                        try {
                            postDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case REPLACE:
                    if (errorCounter == 0) {
                        try {
                            putDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode(), patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case DELETE:
                    if (errorCounter == 0) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case REMOVE:
                    if (errorCounter == 0) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (RestconfDocumentedException e) {
                            LOG.error("Error removing {} by {} operation", patchEntity.getTargetNode().toString(),
                                    patchEntity.getEditId(), e);
                        }
                    }
                    break;
                case MERGE:
                    if (errorCounter == 0) {
                        try {
                            mergeDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
            }
        }

        //TODO: make sure possible global errors are filled up correctly and decide transaction submission based on that
        //globalErrors = new ArrayList<>();
        if (errorCounter == 0) {
            patchTransaction.submit().checkedGet();
            return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), true,
                    globalErrors);
        } else {
            patchTransaction.cancel();
            return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false,
                    globalErrors);
        }
    }

    // POST configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        checkPreconditions();
        return postDataViaTransaction(domDataBroker, CONFIGURATION, path, payload, globalSchema);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return postDataViaTransaction(domDataBrokerService.get(), CONFIGURATION, path,
                    payload, mountPoint.getSchemaContext());
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // DELETE configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataDelete(
            final YangInstanceIdentifier path) {
        checkPreconditions();
        return deleteDataViaTransaction(domDataBroker.newReadWriteTransaction(), CONFIGURATION, path);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataDelete(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return deleteDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // RPC
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(final SchemaPath type, final NormalizedNode<?, ?> input) {
        checkPreconditions();
        if (rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
        LOG.trace("Invoke RPC {} with input: {}", type, input);
        return rpcService.invokeRpc(type, input);
    }

    public void registerToListenDataChanges(final LogicalDatastoreType datastore, final DataChangeScope scope,
            final ListenerAdapter listener) {
        checkPreconditions();

        if (listener.isListening()) {
            return;
        }

        final YangInstanceIdentifier path = listener.getPath();
        final ListenerRegistration<DOMDataChangeListener> registration = domDataBroker.registerDataChangeListener(
                datastore, path, listener, scope);

        listener.setRegistration(registration);
    }

    private NormalizedNode<?, ?> readDataViaTransaction(final DOMDataReadTransaction transaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        LOG.trace("Read {} via Restconf: {}", datastore.name(), path);
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> listenableFuture = transaction.read(datastore, path);
        if (listenableFuture != null) {
            Optional<NormalizedNode<?, ?>> optional;
            try {
                LOG.debug("Reading result data from transaction.");
                optional = listenableFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Exception by reading {} via Restconf: {}", datastore.name(), path, e);
                throw new RestconfDocumentedException("Problem to get data from transaction.", e.getCause());

            }
            if (optional != null) {
                if (optional.isPresent()) {
                    return optional.get();
                }
            }
        }
        return null;
    }

    private CheckedFuture<Void, TransactionCommitFailedException> postDataViaTransaction(
            final DOMDataBroker domDataBroker, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        // FIXME: This is doing correct post for container and list children
        //        not sure if this will work for choice case
        DOMDataReadWriteTransaction transaction = domDataBroker.newReadWriteTransaction();
        if(payload instanceof MapNode) {
            LOG.trace("POST {} via Restconf: {} with payload {}", datastore.name(), path, payload);
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            try {
                transaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            } catch (RuntimeException e) {
                // FIXME: Figure out and catch specific RunTimeExceptions thrown by NETCONF instead of generic one.
                //        to make this cleaner and easier to maintain.
                transaction.cancel();
                transaction = domDataBroker.newReadWriteTransaction();
                LOG.debug("Empty subtree merge failed", e);
            }
            if (!ensureParentsByMerge(datastore, path, transaction, schemaContext)) {
                transaction.cancel();
                transaction = domDataBroker.newReadWriteTransaction();
            }
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                checkItemDoesNotExists(transaction, datastore, childPath);
                transaction.put(datastore, childPath, child);
            }
        } else {
            checkItemDoesNotExists(transaction,datastore, path);
            if(!ensureParentsByMerge(datastore, path, transaction, schemaContext)) {
                transaction.cancel();
                transaction = domDataBroker.newReadWriteTransaction();
            }
            transaction.put(datastore, path, payload);
        }
        return transaction.submit();
    }

    private void postDataWithinTransaction(
            final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        // FIXME: This is doing correct post for container and list children
        //        not sure if this will work for choice case
        if(payload instanceof MapNode) {
            LOG.trace("POST {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            ensureParentsByMerge(datastore, path, rWTransaction, schemaContext);
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                checkItemDoesNotExists(rWTransaction, datastore, childPath);
                rWTransaction.put(datastore, childPath, child);
            }
        } else {
            checkItemDoesNotExists(rWTransaction,datastore, path);
            ensureParentsByMerge(datastore, path, rWTransaction, schemaContext);
            rWTransaction.put(datastore, path, payload);
        }
    }

    private void checkItemExists(final DOMDataReadWriteTransaction rWTransaction,
                                 final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final ListenableFuture<Boolean> futureDatastoreData = rWTransaction.exists(store, path);
        try {
            if (!futureDatastoreData.get()) {
                final String errMsg = "Operation via Restconf was not executed because data does not exist";
                LOG.trace("{}:{}", errMsg, path);
                rWTransaction.cancel();
                throw new RestconfDocumentedException("Data does not exist for path: " + path, ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("It wasn't possible to get data loaded from datastore at path {}", path, e);
        }
    }

    private void checkItemDoesNotExists(final DOMDataReadWriteTransaction rWTransaction,
                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final ListenableFuture<Boolean> futureDatastoreData = rWTransaction.exists(store, path);
        try {
            if (futureDatastoreData.get()) {
                final String errMsg = "Operation via Restconf was not executed because data already exists";
                LOG.trace("{}:{}", errMsg, path);
                rWTransaction.cancel();
                throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                        ErrorTag.DATA_EXISTS);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("It wasn't possible to get data loaded from datastore at path {}", path, e);
        }
    }

    private CheckedFuture<Void, TransactionCommitFailedException> putDataViaTransaction(
            final DOMDataBroker domDataBroker, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext)
    {
        DOMDataReadWriteTransaction transaction = domDataBroker.newReadWriteTransaction();
        LOG.trace("Put {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        if (!ensureParentsByMerge(datastore, path, transaction, schemaContext)) {
            transaction.cancel();
            transaction = domDataBroker.newReadWriteTransaction();
        }
        transaction.put(datastore, path, payload);
        return transaction.submit();
    }

    private void putDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Put {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        ensureParentsByMerge(datastore, path, writeTransaction, schemaContext);
        writeTransaction.put(datastore, path, payload);
    }

    private CheckedFuture<Void, TransactionCommitFailedException> deleteDataViaTransaction(
            final DOMDataReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        LOG.trace("Delete {} via Restconf: {}", datastore.name(), path);
        checkItemExists(readWriteTransaction, datastore, path);
        readWriteTransaction.delete(datastore, path);
        return readWriteTransaction.submit();
    }

    private void deleteDataWithinTransaction(
            final DOMDataWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        LOG.trace("Delete {} within Restconf PATCH: {}", datastore.name(), path);
        writeTransaction.delete(datastore, path);
    }

    private void mergeDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Merge {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        ensureParentsByMerge(datastore, path, writeTransaction, schemaContext);

        // merging is necessary only for lists otherwise we can call put method
        if (payload instanceof MapNode) {
            writeTransaction.merge(datastore, path, payload);
        } else {
            writeTransaction.put(datastore, path, payload);
        }
    }

    public void setDomDataBroker(final DOMDataBroker domDataBroker) {
        this.domDataBroker = domDataBroker;
    }

    private boolean ensureParentsByMerge(final LogicalDatastoreType store,
                                      final YangInstanceIdentifier normalizedPath, final DOMDataReadWriteTransaction rwTx, final SchemaContext schemaContext) {

        boolean mergeResult = true;
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = normalizedPath.getPathArguments().iterator();

        while(it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if(rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }

            // Skip last element, its not a parent
            if(it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        // No parent structure involved, no need to ensure parents
        if(normalizedPathWithoutChildArgs.isEmpty()) {
            return mergeResult;
        }

        Preconditions.checkArgument(rootNormalizedPath != null, "Empty path received");

        final NormalizedNode<?, ?> parentStructure =
                ImmutableNodes.fromInstanceId(schemaContext, YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        try {
            rwTx.merge(store, rootNormalizedPath, parentStructure);
        } catch (RuntimeException e) {
            /*
             * Catching the exception here, logging it and proceeding further
             * for the following reasons.
             *
             * 1. For MD-SAL store if it fails we'll go with the next call
             * anyway and let the failure happen there. 2. For NETCONF devices
             * that can not handle these calls such as creation of empty lists
             * etc, instead of failing we'll go with the actual call. Devices
             * should be able to handle the actual calls made without the need
             * to create parents. So instead of failing we will give a device a
             * chance to configure the management entity in question. 3. If this
             * merge call is handled properly by MD-SAL data store or a Netconf
             * device this is a no-op.
             */
             // FIXME: Figure out and catch specific RunTimeExceptions thrown by NETCONF instead of generic one.
             //        to make this cleaner and easier to maintain.
            mergeResult = false;
            LOG.debug("Exception while creating the parent in ensureParentsByMerge. Proceeding with the actual request", e);
        }
        return mergeResult;
    }
}

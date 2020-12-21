/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only candidate datastore and no writable running.
 * The sequence goes as:
 * <ol>
 *   <li>Lock candidate datastore on tx construction
 *     <ul>
 *       <li>Lock has to succeed, if it does not, an attempt to discard changes is made</li>
 *       <li>Discard changes has to succeed</li>
 *       <li>If discard is successful, lock is reattempted</li>
 *       <li>Second lock attempt has to succeed</li>
 *     </ul>
 *   </li>
 *   <li>Edit-config in candidate N times
 *     <ul>
 *       <li>If any issue occurs during edit,
 *       datastore is discarded using discard-changes rpc, unlocked and an exception is thrown async</li>
 *     </ul>
 *   </li>
 *   <li>Commit and Unlock candidate datastore async</li>
 * </ol>
 */
public class WriteCandidateTx extends AbstractWriteTx {
    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateTx.class);

    public WriteCandidateTx(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport) {
        this(id, netconfOps, rollbackSupport, true);
    }

    public WriteCandidateTx(RemoteDeviceId id, NetconfBaseOps netconfOps, boolean rollbackSupport,
            boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    protected synchronized void init() {
        LOG.trace("{}: Initializing {} transaction", id, getClass().getSimpleName());
        lockCandidate();
    }

    protected void lockCandidate() {
        if (!isLockAllowed) {
            LOG.trace("Lock is not allowed: {}", id);
            lock.setFuture(Futures.immediateFuture(new DefaultDOMRpcResult()));
            return;
        }
        final FutureCallback<DOMRpcResult> lockCandidateCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Lock candidate successful");
                    }
                } else {
                    LOG.warn("{}: lock candidate invoked unsuccessfully: {}", id, result.getErrors());
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Lock candidate operation failed", throwable);
            }
        };
        lock.setFuture(netOps.lockCandidate(lockCandidateCallback));
        resultsFutures.add(lock);
    }

    @Override
    protected void cleanup() {
        discardChanges();
        cleanupOnSuccess();
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void discardChanges() {
        Futures.addCallback(lock, new NetconfRpcFutureCallback("Check datastore lock", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    netOps.discardChanges(new NetconfRpcFutureCallback("Discarding candidate", id));
                }
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public synchronized ListenableFuture<RpcResult<Void>> performCommit() {
        final SettableFuture<RpcResult<Void>> commitResult = SettableFuture.create();
        final ListenableFuture<RpcResult<Void>> txResult = resultsToTxStatus();

        Futures.addCallback(txResult, new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    //remove the previous results as we already processed them
                    resultsFutures.clear();
                    resultsFutures.add(netOps.commit(new FutureCallback<>() {
                        @Override
                        public void onSuccess(final DOMRpcResult result) {
                            if (isSuccess(result)) {
                                cleanupOnSuccess();
                            } else {
                                cleanup();
                            }
                        }

                        @Override
                        public void onFailure(final Throwable throwable) {
                            cleanup();
                        }
                    }));
                    commitResult.setFuture(resultsToTxStatus());
                } else {
                    commitResult.set(result);
                    cleanup();
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                commitResult.setException(throwable);
                cleanup();
            }
        }, MoreExecutors.directExecutor());
        return commitResult;
    }

    protected void cleanupOnSuccess() {
        unlock();
    }

    @Override
    protected void editConfig(final YangInstanceIdentifier path,
                              final Optional<NormalizedNode<?, ?>> data,
                              final DataContainerChild<?, ?> editStructure,
                              final Optional<ModifyAction> defaultOperation,
                              final String operation) {
        final SettableFuture<DOMRpcResult> editResult = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    final NetconfRpcFutureCallback editConfigCallback =
                        new NetconfRpcFutureCallback("Edit candidate", id);
                    if (defaultOperation.isPresent()) {
                        editResult.setFuture(netOps.editConfigCandidate(
                            editConfigCallback, editStructure, defaultOperation.get(), rollbackSupport));
                    } else {
                        editResult.setFuture(
                            netOps.editConfigCandidate(editConfigCallback, editStructure, rollbackSupport));
                    }
                } else {
                    editResult.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                editResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(editResult);
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void unlock() {
        Futures.addCallback(lock, new NetconfRpcFutureCallback("Check datastore lock", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (isLockAllowed) {
                        netOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
                    } else {
                        LOG.trace("Unlock is not allowed: {}", id);
                    }
                }
            }
        }, MoreExecutors.directExecutor());
    }
}

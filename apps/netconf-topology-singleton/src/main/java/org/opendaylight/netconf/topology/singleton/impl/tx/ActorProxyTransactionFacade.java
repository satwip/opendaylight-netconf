/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;

/**
 * ProxyTransactionFacade implementation that interfaces with an actor.
 *
 * @author Thomas Pantelis
 */
class ActorProxyTransactionFacade implements ProxyTransactionFacade {
    private static final Logger LOG = LoggerFactory.getLogger(ActorProxyTransactionFacade.class);

    private final SettableFuture<CommitInfo> settableFuture = SettableFuture.create();
    private final @NonNull FluentFuture<CommitInfo> fluentFuture = FluentFuture.from(settableFuture);
    private final ActorRef masterTxActor;
    private final RemoteDeviceId id;
    private final ExecutionContext executionContext;
    private final Timeout askTimeout;

    ActorProxyTransactionFacade(final ActorRef masterTxActor, final RemoteDeviceId id,
            final ExecutionContext executionContext, final Timeout askTimeout) {
        this.masterTxActor = Objects.requireNonNull(masterTxActor);
        this.id = Objects.requireNonNull(id);
        this.executionContext = Objects.requireNonNull(executionContext);
        this.askTimeout = Objects.requireNonNull(askTimeout);
    }

    @Override
    public Object getIdentifier() {
        return id;
    }

    @Override
    public FluentFuture<?> completionFuture() {
        return fluentFuture;
    }

    @Override
    public boolean cancel() {
        LOG.debug("{}: Cancel via actor {}", id, masterTxActor);

        Patterns.ask(masterTxActor, new CancelRequest(), askTimeout).onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.warn("{}: Cancel failed", id, failure);
                    return;
                }

                LOG.debug("{}: Cancel succeeded", id);
            }
        }, executionContext);

        return true;
    }

    @Override
    public FluentFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Read {} {} via actor {}", id, store, path, masterTxActor);

        final var future = SettableFuture.<Optional<NormalizedNode>>create();
        Patterns.ask(masterTxActor, new ReadRequest(store, path), askTimeout).onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Read {} {} failed", id, store, path, failure);

                    final Throwable processedFailure = processFailure(failure);
                    if (processedFailure instanceof ReadFailedException) {
                        future.setException(processedFailure);
                    } else {
                        future.setException(new ReadFailedException("Read of store " + store + " path " + path
                            + " failed", processedFailure));
                    }
                    return;
                }

                LOG.debug("{}: Read {} {} succeeded: {}", id, store, path, response);

                if (response instanceof EmptyReadResponse) {
                    future.set(Optional.empty());
                    return;
                }

                if (response instanceof NormalizedNodeMessage data) {
                    future.set(Optional.of(data.getNode()));
                }
            }
        }, executionContext);

        return FluentFuture.from(future);
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.debug("{}: Exists {} {} via actor {}", id, store, path, masterTxActor);

        final var future = SettableFuture.<Boolean>create();

        Patterns.ask(masterTxActor, new ExistsRequest(store, path), askTimeout).onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Exists {} {} failed", id, store, path, failure);

                    final Throwable processedFailure = processFailure(failure);
                    if (processedFailure instanceof ReadFailedException) {
                        future.setException(processedFailure);
                    } else {
                        future.setException(new ReadFailedException("Exists of store " + store + " path " + path
                            + " failed", processedFailure));
                    }
                    return;
                }

                LOG.debug("{}: Exists {} {} succeeded: {}", id, store, path, response);

                future.set((Boolean) response);
            }
        }, executionContext);

        return FluentFuture.from(future);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {} via actor {}", id, store, path, masterTxActor);
        masterTxActor.tell(new DeleteRequest(store, path), ActorRef.noSender());
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode data) {
        LOG.debug("{}: Put {} {} via actor {}", id, store, path, masterTxActor);
        masterTxActor.tell(new PutRequest(store, new NormalizedNodeMessage(path, data)), ActorRef.noSender());
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode data) {
        LOG.debug("{}: Merge {} {} via actor {}", id, store, path, masterTxActor);
        masterTxActor.tell(new MergeRequest(store, new NormalizedNodeMessage(path, data)), ActorRef.noSender());
    }

    @Override
    public FluentFuture<? extends CommitInfo> commit() {
        LOG.debug("{}: Commit via actor {}", id, masterTxActor);

        Patterns.ask(masterTxActor, new SubmitRequest(), askTimeout).onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Commit failed", id, failure);
                    settableFuture.setException(newTransactionCommitFailedException(processFailure(failure)));
                    return;
                }

                LOG.debug("{}: Commit succeeded", id);

                settableFuture.set(CommitInfo.empty());
            }

            private TransactionCommitFailedException newTransactionCommitFailedException(final Throwable failure) {
                return new TransactionCommitFailedException(String.format("%s: Commit of transaction failed",
                    getIdentifier()), failure);
            }
        }, executionContext);

        return FluentFuture.from(settableFuture);
    }

    private Throwable processFailure(final Throwable failure) {
        return failure instanceof AskTimeoutException
                ? NetconfTopologyUtils.createMasterIsDownException(id, (Exception)failure) : failure;
    }
}

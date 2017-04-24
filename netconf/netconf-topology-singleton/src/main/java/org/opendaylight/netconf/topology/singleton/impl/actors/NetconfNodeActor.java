/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMRpcService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyYangTextSourceProvider;
import org.opendaylight.netconf.topology.singleton.impl.SlaveSalFacade;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfNodeActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeActor.class);

    private NetconfTopologySetup setup;
    private RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final SchemaRepository schemaRepository;

    private List<SourceIdentifier> sourceIdentifiers;
    private List<SchemaSourceRegistration> schemaSourceRegistrations;
    private DOMRpcService deviceRpc;
    private SlaveSalFacade slaveSalManager;
    private DOMDataBroker deviceDataBroker;
    //readTxActor can be shared
    private ActorRef readTxActor;

    public static Props props(final NetconfTopologySetup setup,
                              final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                              final SchemaRepository schemaRepository) {
        return Props.create(NetconfNodeActor.class, () ->
                new NetconfNodeActor(setup, id, schemaRegistry, schemaRepository));
    }

    private NetconfNodeActor(final NetconfTopologySetup setup,
                             final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                             final SchemaRepository schemaRepository) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = schemaRegistry;
        this.schemaRepository = schemaRepository;
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CreateInitialMasterActorData) { // master

            final CreateInitialMasterActorData masterActorData = (CreateInitialMasterActorData) message;
            sourceIdentifiers = masterActorData.getSourceIndentifiers();
            this.deviceDataBroker = masterActorData.getDeviceDataBroker();
            final DOMDataReadOnlyTransaction tx = deviceDataBroker.newReadOnlyTransaction();
            readTxActor = context().actorOf(ReadTransactionActor.props(tx));
            this.deviceRpc = masterActorData.getDeviceRpc();

            sender().tell(new MasterActorDataInitialized(), self());

            LOG.debug("{}: Master is ready.", id);

        } else if (message instanceof RefreshSetupMasterActorData) {
            setup = ((RefreshSetupMasterActorData) message).getNetconfTopologyDeviceSetup();
            id = ((RefreshSetupMasterActorData) message).getRemoteDeviceId();
            sender().tell(new MasterActorDataInitialized(), self());
        } else if (message instanceof AskForMasterMountPoint) { // master
            // only master contains reference to deviceDataBroker
            if (deviceDataBroker != null) {
                LOG.debug("Received AskForMasterMountPoint");
                getSender().tell(new RegisterMountPoint(sourceIdentifiers), getSelf());
            }

        } else if (message instanceof YangTextSchemaSourceRequest) { // master

            final YangTextSchemaSourceRequest yangTextSchemaSourceRequest = (YangTextSchemaSourceRequest) message;
            LOG.debug("Received YangTextSchemaSourceRequest {}" + yangTextSchemaSourceRequest.getSourceIdentifier());
            sendYangTextSchemaSourceProxy(yangTextSchemaSourceRequest.getSourceIdentifier(), sender());

        } else if (message instanceof NewReadTransactionRequest) { // master

            sender().tell(new NewReadTransactionReply(readTxActor), self());

        } else if (message instanceof NewWriteTransactionRequest) { // master
            try {
                final DOMDataWriteTransaction tx = deviceDataBroker.newWriteOnlyTransaction();
                final ActorRef txActor = context().actorOf(WriteTransactionActor.props(tx));
                sender().tell(new NewWriteTransactionReply(txActor), self());
            } catch (final Throwable t) {
                sender().tell(t, self());
            }

        } else if (message instanceof InvokeRpcMessage) { // master

            final InvokeRpcMessage invokeRpcMessage = ((InvokeRpcMessage) message);
            invokeSlaveRpc(invokeRpcMessage.getSchemaPath(), invokeRpcMessage.getNormalizedNodeMessage(), sender());

        } else if (message instanceof RegisterMountPoint) { //slaves
            sourceIdentifiers = ((RegisterMountPoint) message).getSourceIndentifiers();
            LOG.debug("Received RegisterMountPoint");
            LOG.debug("Source identifiers: {}", sourceIdentifiers);
            registerSlaveMountPoint(getSender());

        } else if (message instanceof UnregisterSlaveMountPoint) { //slaves
            if (slaveSalManager != null) {
                slaveSalManager.close();
                slaveSalManager = null;
            }

        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        if (schemaSourceRegistrations != null) {
            schemaSourceRegistrations.forEach(SchemaSourceRegistration::close);
        }
    }

    private void sendYangTextSchemaSourceProxy(final SourceIdentifier sourceIdentifier, final ActorRef sender) {
        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> yangTextSchemaSource =
                schemaRepository.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        Futures.addCallback(yangTextSchemaSource, new FutureCallback<YangTextSchemaSource>() {
            @Override
            public void onSuccess(final YangTextSchemaSource yangTextSchemaSource) {
                try {
                    LOG.debug("Sending schema source for {}", sourceIdentifier);
                    sender.tell(new YangTextSchemaSourceSerializationProxy(yangTextSchemaSource), getSelf());
                } catch (final IOException exception) {
                    LOG.debug("Failed to read schema source for {}", sourceIdentifier);
                    sender.tell(exception.getCause(), getSelf());
                }
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                LOG.debug("Can't get schema source for {}", sourceIdentifier);
                sender.tell(throwable, getSelf());
            }
        });
    }

    private void invokeSlaveRpc(final SchemaPath schemaPath, final NormalizedNodeMessage normalizedNodeMessage,
                                final ActorRef recipient) {

        final CheckedFuture<DOMRpcResult, DOMRpcException> rpcResult =
                deviceRpc.invokeRpc(schemaPath, normalizedNodeMessage.getNode());

        Futures.addCallback(rpcResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(@Nullable final DOMRpcResult domRpcResult) {
                if (domRpcResult == null) {
                    recipient.tell(new EmptyResultResponse(), getSender());
                    return;
                }
                NormalizedNodeMessage nodeMessageReply = null;
                if (domRpcResult.getResult() != null) {
                    nodeMessageReply = new NormalizedNodeMessage(YangInstanceIdentifier.EMPTY,
                            domRpcResult.getResult());
                }
                recipient.tell(new InvokeRpcMessageReply(nodeMessageReply, domRpcResult.getErrors()), getSelf());
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                recipient.tell(throwable, getSelf());
            }
        });
    }

    private void registerSlaveMountPoint(final ActorRef masterReference) {
        if (this.slaveSalManager != null) {
            slaveSalManager.close();
        }
        slaveSalManager = new SlaveSalFacade(id, setup.getDomBroker(), setup.getActorSystem());

        final CheckedFuture<SchemaContext, SchemaResolutionException> remoteSchemaContext =
                getSchemaContext(masterReference);
        final DOMRpcService deviceRpc = getDOMRpcService(masterReference);

        Futures.addCallback(remoteSchemaContext, new FutureCallback<SchemaContext>() {
            @Override
            public void onSuccess(final SchemaContext result) {
                LOG.info("{}: Schema context resolved: {}", id, result.getModules());
                slaveSalManager.registerSlaveMountPoint(result, deviceRpc, masterReference);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                LOG.error("{}: Failed to register mount point: {}", id, throwable);
            }
        });
    }

    private DOMRpcService getDOMRpcService(final ActorRef masterReference) {
        return new ProxyDOMRpcService(setup.getActorSystem(), masterReference, id);
    }

    private CheckedFuture<SchemaContext, SchemaResolutionException> getSchemaContext(final ActorRef masterReference) {

        final RemoteYangTextSourceProvider remoteYangTextSourceProvider =
                new ProxyYangTextSourceProvider(masterReference, getContext());
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider,
                getContext().dispatcher());
        LOG.debug("Registering schema sources");
        schemaSourceRegistrations = sourceIdentifiers.stream()
                .map(sourceId -> schemaRegistry.registerSchemaSource(remoteProvider, PotentialSchemaSource.create(sourceId,
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())))
                .collect(Collectors.toList());
        LOG.debug("Creating SchemaContextFactory");
        final SchemaContextFactory schemaContextFactory
                = schemaRepository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
        LOG.debug("Creating SchemaContext");
        return schemaContextFactory.createSchemaContext(sourceIdentifiers);
    }

}

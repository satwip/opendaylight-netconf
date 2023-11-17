/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.server.api.OperationsContent;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
// FIXME: this should live in 'org.opendaylight.restconf.server.mdsal' package
@Singleton
@Component(service = { MdsalRestconfServer.class, RestconfServer.class })
public final class MdsalRestconfServer implements RestconfServer {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final String YANG_LIBRARY_REVISION = YangLibrary.QNAME.getRevision().orElseThrow().toString();
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", RestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull DatabindProvider databindProvider;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;

    @SuppressWarnings("unused")
    private volatile RestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DatabindProvider databindProvider,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMMountPointService mountPointService,
            @Reference final List<RpcImplementation> localRpcs) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.mountPointService = requireNonNull(mountPointService);
        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
    }

    public MdsalRestconfServer(final DatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMMountPointService mountPointService,
            final RpcImplementation... localRpcs) {
        this(databindProvider, dataBroker, rpcService, mountPointService, List.of(localRpcs));
    }

    @NonNull InstanceIdentifierContext bindRequestPath(final String identifier) {
        return bindRequestPath(databindProvider.currentContext(), identifier);
    }

    @Deprecated
    @NonNull InstanceIdentifierContext bindRequestPath(final DatabindContext databind, final String identifier) {
        // FIXME: go through ApiPath first. That part should eventually live in callers
        // FIXME: DatabindContext looks like it should be internal
        return verifyNotNull(ParserIdentifier.toInstanceIdentifier(requireNonNull(identifier), databind.modelContext(),
            mountPointService));
    }

    @Override
    public OperationsContent operationsGET() {
        return operationsGET(databindProvider.currentContext().modelContext());
    }

    @Override
    public OperationsContent operationsGET(final String operation) {
        // get current module RPCs/actions by RPC/action name
        final var inference = bindRequestPath(operation).inference();
        if (inference.isEmpty()) {
            return operationsGET(inference.getEffectiveModelContext());
        }

        final var stmt = inference.toSchemaInferenceStack().currentStatement();
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return new OperationsContent.Leaf(inference.getEffectiveModelContext(), rpc.argument());
        }
        LOG.debug("Operation '{}' resulted in non-RPC {}", operation, stmt);
        return null;
    }

    private static @NonNull OperationsContent operationsGET(final EffectiveModelContext modelContext) {
        final var modules = modelContext.getModuleStatements();
        if (modules.isEmpty()) {
            // No modules, or defensive return empty content
            return new OperationsContent.Container(modelContext, ImmutableSetMultimap.of());
        }

        // RPCs by their XMLNamespace/Revision
        final var table = HashBasedTable.<XMLNamespace, Revision, ImmutableSet<QName>>create();
        for (var entry : modules.entrySet()) {
            final var module = entry.getValue();
            final var rpcNames = module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                .map(RpcEffectiveStatement::argument)
                .collect(ImmutableSet.toImmutableSet());
            if (!rpcNames.isEmpty()) {
                final var namespace = entry.getKey();
                table.put(namespace.getNamespace(), namespace.getRevision().orElse(null), rpcNames);
            }
        }

        // Now pick the latest revision for each namespace
        final var rpcs = ImmutableSetMultimap.<QNameModule, QName>builder();
        for (var entry : table.rowMap().entrySet()) {
            entry.getValue().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey, (first, second) -> Revision.compare(second, first)))
                .findFirst()
                .ifPresent(row -> rpcs.putAll(QNameModule.create(entry.getKey(), row.getKey()), row.getValue()));
        }
        return new OperationsContent.Container(modelContext, rpcs.build());
    }

    @Override
    public RestconfFuture<OperationOutput> operationsPOST(final URI restconfURI, final String apiPath,
            final OperationInputBody body) {
        final var currentContext = databindProvider.currentContext();
        final var reqPath = bindRequestPath(currentContext, apiPath);
        final var inference = reqPath.inference();
        final ContainerNode input;
        try {
            input = body.toContainerNode(inference);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }

        return getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint())
            .invokeRpc(restconfURI, reqPath.getSchemaNode().getQName(),
                new OperationInput(currentContext, inference, input));
    }

    @Override
    public NormalizedNodePayload yangLibraryVersionGET() {
        final var stack = SchemaInferenceStack.of(databindProvider.currentContext().modelContext());
        stack.enterYangData(YangApi.NAME);
        stack.enterDataTree(Restconf.QNAME);
        stack.enterDataTree(YANG_LIBRARY_VERSION);
        return new NormalizedNodePayload(stack.toInference(),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, YANG_LIBRARY_REVISION));
    }

    @NonNull InstanceIdentifierContext bindRequestRoot() {
        return InstanceIdentifierContext.ofLocalRoot(databindProvider.currentContext().modelContext());
    }

    @VisibleForTesting
    @NonNull RestconfStrategy getRestconfStrategy(final EffectiveModelContext modelContext,
            final @Nullable DOMMountPoint mountPoint) {
        if (mountPoint == null) {
            return localStrategy(modelContext);
        }

        final var ret = RestconfStrategy.forMountPoint(modelContext, mountPoint);
        if (ret == null) {
            final var mountId = mountPoint.getIdentifier();
            LOG.warn("Mount point {} does not expose a suitable access interface", mountId);
            throw new RestconfDocumentedException("Could not find a supported access interface in mount point "
                + mountId);
        }
        return ret;
    }

    private @NonNull RestconfStrategy localStrategy(final EffectiveModelContext modelContext) {
        final var local = (RestconfStrategy) LOCAL_STRATEGY.getAcquire(this);
        if (local != null && modelContext.equals(local.modelContext())) {
            return local;
        }

        final var created = new MdsalRestconfStrategy(modelContext, dataBroker, rpcService, localRpcs);
        LOCAL_STRATEGY.setRelease(this, created);
        return created;
    }
}

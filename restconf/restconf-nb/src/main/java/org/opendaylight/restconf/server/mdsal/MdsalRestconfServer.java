/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.databind.ChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.DataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataPatchPath;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.api.DataPutPath;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationsGetResult;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinTextSchemaSource;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
@Singleton
@Component(service = { RestconfServer.class, DatabindProvider.class })
public final class MdsalRestconfServer
        implements RestconfServer, DatabindProvider, EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", MdsalRestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;
    private final @Nullable DOMActionService actionService;
    private final @Nullable DOMYangTextSourceProvider sourceProvider;

    private final Registration reg;

    @SuppressWarnings("unused")
    private volatile MdsalRestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DOMSchemaService schemaService,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMActionService actionService,
            @Reference final DOMMountPointService mountPointService,
            @Reference final List<RpcImplementation> localRpcs) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.actionService = requireNonNull(actionService);
        this.mountPointService = requireNonNull(mountPointService);
        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
        sourceProvider = schemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);

        localStrategy = createLocalStrategy(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this);
    }

    public MdsalRestconfServer(final DOMSchemaService schemaService, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(schemaService, dataBroker, rpcService, actionService, mountPointService, List.of(localRpcs));
    }

    @Override
    public DatabindContext currentDatabind() {
        return localStrategy().databind();
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var local = localStrategy();
        if (!newModelContext.equals(local.modelContext())) {
            LOCAL_STRATEGY.setRelease(this, createLocalStrategy(newModelContext));
        }
    }

    private @NonNull MdsalRestconfStrategy createLocalStrategy(final EffectiveModelContext modelContext) {
        return new MdsalRestconfStrategy(DatabindContext.ofModel(modelContext), dataBroker, rpcService, actionService,
            sourceProvider, mountPointService, localRpcs);
    }

    private @NonNull MdsalRestconfStrategy localStrategy() {
        return verifyNotNull((MdsalRestconfStrategy) LOCAL_STRATEGY.getAcquire(this));
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        localStrategy = null;
    }

    @Override
    public RestconfFuture<Empty> dataDELETE(final ApiPath identifier) {
        final var stratAndPath = localStrategy().resolveStrategyPath(identifier);
        return stratAndPath.strategy().delete(stratAndPath.path().instance());
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final DataGetParams params) {
        return localStrategy().dataGET(ApiPath.empty(), params);
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final ApiPath identifier, final DataGetParams params) {
        final var stratAndTail = localStrategy().resolveStrategy(identifier);
        return stratAndTail.strategy().dataGET(stratAndTail.tail(), params);
    }

    @Override
    public RestconfFuture<Empty> dataPATCH(final ResourceBody body) {
        final var strategy = localStrategy();
        return dataPATCH(strategy, new DataPutPath(strategy.databind()), body);
    }

    @Override
    public RestconfFuture<Empty> dataPATCH(final ApiPath identifier, final ResourceBody body) {
        final var strategyAndPath = localStrategy().resolveStrategyPath(identifier);
        final var strategy = strategyAndPath.strategy();
        final var path = strategyAndPath.path();
        return dataPATCH(strategy, new DataPutPath(strategy.databind(), path.inference(), path.instance()), body);
    }

    private static @NonNull RestconfFuture<Empty> dataPATCH(final RestconfStrategy strategy, final DataPutPath path,
            final ResourceBody body) {
        final NormalizedNode data;
        try {
            data = body.toNormalizedNode(path);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return strategy.merge(path.instance(), data);
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final PatchBody body) {
        final var strategy = localStrategy();
        return dataPATCH(strategy, new DataPatchPath(strategy.databind(), YangInstanceIdentifier.of()), body);
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final ApiPath identifier, final PatchBody body) {
        final var stratAndPath = localStrategy().resolveStrategyPath(identifier);
        final var strategy = stratAndPath.strategy();
        return dataPATCH(strategy, new DataPatchPath(strategy.databind(), stratAndPath.path().instance()), body);
    }

    private static @NonNull RestconfFuture<PatchStatusContext> dataPATCH(final RestconfStrategy strategy,
            final DataPatchPath path, final PatchBody body) {
        final PatchContext patch;
        try {
            patch = body.toPatchContext(path);
        } catch (IOException e) {
            LOG.debug("Error parsing YANG Patch input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }
        return strategy.patchData(patch);
    }

    @Override
    public RestconfFuture<CreateResource> dataPOST(final ChildBody body, final Map<String, String> queryParameters) {
        return localStrategy().dataCreatePOST(body, queryParameters);
    }

    @Override
    public RestconfFuture<? extends DataPostResult> dataPOST(final ApiPath identifier, final DataPostBody body,
            final Map<String, String> queryParameters) {
        final var strategyAndTail = localStrategy().resolveStrategy(identifier);
        return strategyAndTail.strategy().dataPOST(strategyAndTail.tail(), body, queryParameters);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ResourceBody body, final Map<String, String> query) {
        return localStrategy().dataPUT(ApiPath.empty(), body, query);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ApiPath identifier, final ResourceBody body,
             final Map<String, String> queryParameters) {
        final var strategyAndTail = localStrategy().resolveStrategy(identifier);
        return strategyAndTail.strategy().dataPUT(strategyAndTail.tail(), body, queryParameters);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YangTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YangTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YinTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YinTextSchemaSource.class);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final String fileName, final String revision,
            final Class<? extends SchemaSourceRepresentation> representation) {
        return modulesGET(localStrategy(), fileName, revision, representation);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final ApiPath mountPath, final String fileName,
            final String revision, final Class<? extends SchemaSourceRepresentation> representation) {
        final var mountOffset = mountPath.indexOf("yang-ext", "mount");
        if (mountOffset != mountPath.steps().size() - 1) {
            return RestconfFuture.failed(new RestconfDocumentedException("Mount path has to end with yang-ext:mount"));
        }

        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(mountPath);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        // FIXME: require remnant to be empty
        return modulesGET(stratAndTail.strategy(), fileName, revision, representation);
    }

    private static @NonNull RestconfFuture<ModulesGetResult> modulesGET(final RestconfStrategy strategy,
            final String moduleName, final String revisionStr,
            final Class<? extends SchemaSourceRepresentation> representation) {
        if (moduleName == null) {
            return RestconfFuture.failed(new RestconfDocumentedException("Module name must be supplied",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (moduleName.isEmpty() || !YangNames.IDENTIFIER_START.matches(moduleName.charAt(0))) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Identifier must start with character from set 'a-zA-Z_", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (moduleName.toUpperCase(Locale.ROOT).startsWith("XML")) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Identifier must NOT start with XML ignore case", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (YangNames.NOT_IDENTIFIER_PART.matchesAnyOf(moduleName.substring(1))) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Supplied name has not expected identifier format", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }

        // YANG Revision-compliant string is required
        final Revision revision;
        try {
            revision = Revision.ofNullable(revisionStr).orElse(null);
        } catch (final DateTimeParseException e) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Supplied revision is not in expected date format YYYY-mm-dd",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
        }

        return strategy.resolveSource(new SourceIdentifier(moduleName, revision), representation)
            .transform(ModulesGetResult::new);
    }

    @Override
    public RestconfFuture<OperationsGetResult> operationsGET() {
        return localStrategy().operationsGET();
    }

    @Override
    public RestconfFuture<OperationsGetResult> operationsGET(final ApiPath operation) {
        final var strategyAndTail = localStrategy().resolveStrategy(operation);
        return strategyAndTail.strategy().operationsGET(strategyAndTail.tail());
    }

    @Override
    public RestconfFuture<OperationsPostResult> operationsPOST(final URI restconfURI, final ApiPath apiPath,
            final OperationInputBody body) {
        final var strategyAndTail = localStrategy().resolveStrategy(apiPath);
        final var strategy = strategyAndTail.strategy();
        return strategy.operationsPOST(restconfURI, strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> yangLibraryVersionGET() {
        final var stack = SchemaInferenceStack.of(localStrategy().modelContext());
        try {
            stack.enterYangData(YangApi.NAME);
            stack.enterDataTree(Restconf.QNAME);
            stack.enterDataTree(YANG_LIBRARY_VERSION);
        } catch (IllegalArgumentException e) {
            return RestconfFuture.failed(new RestconfDocumentedException("RESTCONF is not available"));
        }
        return RestconfFuture.of(new NormalizedNodePayload(stack.toInference(),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, stack.getEffectiveModelContext()
                .findModuleStatements("ietf-yang-library").iterator().next().localQNameModule().getRevision()
                .map(Revision::toString).orElse(""))));
    }
}

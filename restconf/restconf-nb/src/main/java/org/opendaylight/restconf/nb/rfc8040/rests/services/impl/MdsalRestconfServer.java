/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.CreateResourceMode;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.InvokeOperationMode;
import org.opendaylight.restconf.nb.rfc8040.databind.POSTMode;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceMode;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
// FIXME: factor out the 'RestconfServer' interface once we're ready
// FIXME: this should live in 'org.opendaylight.restconf.server.mdsal' package
@Singleton
@Component
public final class MdsalRestconfServer {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", RestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull DatabindProvider databindProvider;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;

    @SuppressWarnings("unused")
    private volatile RestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DatabindProvider databindProvider,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMMountPointService mountPointService) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.mountPointService = requireNonNull(mountPointService);
    }

    @NonNull CreateResourceMode bindPOST() {
        return new CreateResourceMode(YangInstanceIdentifier.of(),
            Inference.ofDataTreePath(databindProvider.currentContext().modelContext()), null);
    }

    @NonNull POSTMode bindPOST(final String identifier) {
        final var databind = databindProvider.currentContext();
        // FIXME: go through ApiPath first. That part should eventually live in callers
        final var iid = ParserIdentifier.toInstanceIdentifier(identifier, databind.modelContext(), mountPointService);
        if (iid.getSchemaNode() instanceof OperationDefinition) {
            return new InvokeOperationMode(databind, iid.getInstanceIdentifier(), iid.inference(), iid.getMountPoint());
        }
        return new CreateResourceMode(iid.getInstanceIdentifier(), iid.inference(), iid.getMountPoint());
    }

    @NonNull ResourceMode bindResource() {
        final var databind = databindProvider.currentContext();
        return new ResourceMode(databind, Inference.ofDataTreePath(databind.modelContext()),
            YangInstanceIdentifier.of(), null);
    }

    @NonNull ResourceMode bindResource(final String identifier) {
        final var databind = databindProvider.currentContext();
        // FIXME: go through ApiPath first. That part should eventually live in callers
        // FIXME: DatabindContext looks like it should be internal
        final var iid = ParserIdentifier.toInstanceIdentifier(identifier, databind.modelContext(), mountPointService);
        return new ResourceMode(databind, iid.inference(), iid.getInstanceIdentifier(), iid.getMountPoint());
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

        final var created = new MdsalRestconfStrategy(modelContext, dataBroker, rpcService);
        LOCAL_STRATEGY.setRelease(this, created);
        return created;
    }
}

/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.netconf.api.capability.BasicCapability;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.capability.YangModuleCapability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.Submodule;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = NetconfOperationServiceFactory.class, immediate = true, property = "type=mdsal-netconf-connector")
public final class MdsalNetconfOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalNetconfOperationServiceFactory.class);
    private static final BasicCapability VALIDATE_CAPABILITY =
            new BasicCapability("urn:ietf:params:netconf:capability:validate:1.0");

    private final DOMDataBroker dataBroker;
    private final DOMRpcService rpcService;

    private final CurrentSchemaContext currentSchemaContext;
    private final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;

    @Activate
    public MdsalNetconfOperationServiceFactory(@Reference final DOMSchemaService schemaService,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.netconfOperationServiceFactoryListener = requireNonNull(netconfOperationServiceFactoryListener);

        rootSchemaSourceProviderDependency = schemaService.getExtensions()
                .getInstance(DOMYangTextSourceProvider.class);
        currentSchemaContext = CurrentSchemaContext.create(requireNonNull(schemaService),
                rootSchemaSourceProviderDependency);
        netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Deactivate
    @Override
    public void close() {
        netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
        currentSchemaContext.close();
    }

    @Override
    public MdsalNetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new MdsalNetconfOperationService(currentSchemaContext, netconfSessionIdForReporting, dataBroker,
                rpcService);
    }

    @Override
    public Set<Capability> getCapabilities() {
        return transformCapabilities(currentSchemaContext.getCurrentContext(), rootSchemaSourceProviderDependency);
    }

    static Set<Capability> transformCapabilities(
            final SchemaContext currentContext,
            final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency) {
        final Set<Capability> capabilities = new HashSet<>();

        // Added by netconf-impl by default
        // capabilities.add(new BasicCapability("urn:ietf:params:netconf:capability:candidate:1.0"));

        for (final Module module : currentContext.getModules()) {
            Optional<YangModuleCapability> cap = moduleToCapability(module, rootSchemaSourceProviderDependency);
            if (cap.isPresent()) {
                capabilities.add(cap.get());
            }
            for (final Submodule submodule : module.getSubmodules()) {
                cap = moduleToCapability(submodule, rootSchemaSourceProviderDependency);
                if (cap.isPresent()) {
                    capabilities.add(cap.get());
                }
            }
        }

        return capabilities;
    }

    private static Optional<YangModuleCapability> moduleToCapability(final ModuleLike module,
            final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency) {
        final SourceIdentifier moduleSourceIdentifier = new SourceIdentifier(module.getName(),
                module.getRevision().map(Revision::toString).orElse(null));

        InputStream sourceStream = null;
        String source;
        try {
            sourceStream = rootSchemaSourceProviderDependency.getSource(moduleSourceIdentifier).get().openStream();
            source = CharStreams.toString(new InputStreamReader(sourceStream, StandardCharsets.UTF_8));
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOG.warn("Ignoring source for module {}. Unable to read content", moduleSourceIdentifier, e);
            source = null;
        }

        try {
            if (sourceStream != null) {
                sourceStream.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing yang source stream {}. Ignoring", moduleSourceIdentifier, e);
        }

        if (source != null) {
            return Optional.of(new YangModuleCapability(module, source));
        }

        LOG.warn("Missing source for module {}. This module will not be available from netconf server",
            moduleSourceIdentifier);
        return Optional.empty();
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        // Advertise validate capability only if DOMDataBroker provides DOMDataTransactionValidator
        if (dataBroker.getExtensions().get(DOMDataTransactionValidator.class) != null) {
            listener.onCapabilitiesChanged(Set.of(VALIDATE_CAPABILITY), Set.of());
        }
        // Advertise namespaces of supported YANG models as NETCONF capabilities
        return currentSchemaContext.registerCapabilityListener(listener);
    }
}

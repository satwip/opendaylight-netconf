/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ConflictingModificationAppliedException;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.Submodule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaContextHandler}.
 */
// FIXME: this really is a service which is maintaining ietf-yang-library contents inside the datastore. It really
//        should live in MD-SAL and be a dynamic store fragment. As a first step we should be turning this into a
//        completely standalone application.
@Singleton
public class SchemaContextHandler implements EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    private final AtomicInteger moduleSetId = new AtomicInteger(0);

    private final DOMDataBroker domDataBroker;
    private final DOMSchemaService domSchemaService;
    private ListenerRegistration<?> listenerRegistration;

    private volatile EffectiveModelContext schemaContext;

    @Inject
    public SchemaContextHandler(@Reference final DOMDataBroker domDataBroker,
            @Reference final DOMSchemaService domSchemaService) {
        this.domDataBroker = requireNonNull(domDataBroker);
        this.domSchemaService = requireNonNull(domSchemaService);
    }

    @PostConstruct
    public void init() {
        listenerRegistration = domSchemaService.registerSchemaContextListener(this);
    }

    @Override
    @PreDestroy
    public void close() {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext context) {
        schemaContext = requireNonNull(context);

        if (context.findModule(IetfYangLibrary.MODULE_QNAME).isPresent()) {
            putData(mapModulesByIetfYangLibraryYang(context.getModules(), context,
                String.valueOf(this.moduleSetId.incrementAndGet())));
        }

        final Module monitoringModule = schemaContext.findModule(RestconfState.QNAME.getModule()).orElse(null);
        if (monitoringModule != null) {
            putData(RestconfMappingNodeUtil.mapCapabilites(monitoringModule));
        }
    }

    public EffectiveModelContext get() {
        return schemaContext;
    }

    private void putData(final ContainerNode normNode) {
        final DOMDataTreeWriteTransaction wTx = domDataBroker.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.create(NodeIdentifier.create(normNode.getIdentifier().getNodeType())), normNode);
        try {
            wTx.commit().get();
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
        } catch (ExecutionException e) {
            final TransactionCommitFailedException failure = Throwables.getCauseAs(e,
                TransactionCommitFailedException.class);
            if (failure.getCause() instanceof ConflictingModificationAppliedException) {
                /*
                 * Ignore error when another cluster node is already putting the same data to DS.
                 * We expect that cluster is homogeneous and that node was going to write the same data
                 * (that means no retry is needed). Transaction chain reset must be invoked to be able
                 * to continue writing data with another transaction after failed transaction.
                 * This is workaround for bug https://bugs.opendaylight.org/show_bug.cgi?id=7728
                 */
                LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);
            } else {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", failure);
            }
        }
    }


    /**
     * Map data from modules to {@link NormalizedNode}.
     *
     * @param modules modules for mapping
     * @param context schema context
     * @param moduleSetId module-set-id of actual set
     * @return mapped data as {@link NormalizedNode}
     */
    @VisibleForTesting
    public static ContainerNode mapModulesByIetfYangLibraryYang(final Collection<? extends Module> modules,
            final SchemaContext context, final String moduleSetId) {
        final CollectionNodeBuilder<MapEntryNode, UserMapNode> mapBuilder = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.MODULE_QNAME_LIST));
        for (final Module module : context.getModules()) {
            fillMapByModules(mapBuilder, IetfYangLibrary.MODULE_QNAME_LIST, false, module, context);
        }
        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(ModulesState.QNAME))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.MODULE_SET_ID_LEAF_QNAME, moduleSetId))
            .withChild(mapBuilder.build()).build();
    }


    /**
     * Map data by the specific module or submodule.
     *
     * @param mapBuilder ordered list builder for children
     * @param mapQName QName corresponding to the list builder
     * @param isSubmodule true if module is specified as submodule, false otherwise
     * @param module specific module or submodule
     * @param context schema context
     */
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, UserMapNode> mapBuilder,
            final QName mapQName, final boolean isSubmodule, final ModuleLike module, final SchemaContext context) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
            newCommonLeafsMapEntryBuilder(mapQName, module);

        mapEntryBuilder.withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_SCHEMA_LEAF_QNAME,
            IetfYangLibrary.BASE_URI_OF_SCHEMA + module.getName() + "/"
            // FIXME: orElse(null) does not seem appropriate here
            + module.getQNameModule().getRevision().map(Revision::toString).orElse(null)));

        if (!isSubmodule) {
            mapEntryBuilder.withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME,
                module.getNamespace().toString()));

            // features - not mandatory
            if (module.getFeatures() != null && !module.getFeatures().isEmpty()) {
                addFeatureLeafList(mapEntryBuilder, module.getFeatures());
            }
            // deviations - not mandatory
            final ConformanceType conformance;
            if (module.getDeviations() != null && !module.getDeviations().isEmpty()) {
                addDeviationList(module, mapEntryBuilder, context);
                conformance = ConformanceType.Implement;
            } else {
                conformance = ConformanceType.Import;
            }
            mapEntryBuilder.withChild(
                ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME, conformance.getName()));

            // submodules - not mandatory
            if (module.getSubmodules() != null && !module.getSubmodules().isEmpty()) {
                addSubmodules(module, mapEntryBuilder, context);
            }
        }
        mapBuilder.withChild(mapEntryBuilder.build());
    }

    /**
     * Mapping submodules of specific module.
     *
     * @param module module with submodules
     * @param mapEntryBuilder mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule ietf-yang-library module
     * @param context schema context
     */
    private static void addSubmodules(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final SchemaContext context) {
        final CollectionNodeBuilder<MapEntryNode, UserMapNode> mapBuilder = Builders.orderedMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_SUBMODULE_LIST_QNAME));

        for (final Submodule submodule : module.getSubmodules()) {
            fillMapByModules(mapBuilder, IetfYangLibrary.SPECIFIC_MODULE_SUBMODULE_LIST_QNAME, true, submodule,
                context);
        }
        mapEntryBuilder.withChild(mapBuilder.build());
    }

    /**
     * Mapping deviations of specific module.
     *
     * @param module
     *             module with deviations
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param context
     *             schema context
     */
    private static void addDeviationList(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final SchemaContext context) {
        final CollectionNodeBuilder<MapEntryNode, SystemMapNode> deviations = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME));
        for (final Deviation deviation : module.getDeviations()) {
            final List<QName> ids = deviation.getTargetPath().getNodeIdentifiers();
            final QName lastComponent = ids.get(ids.size() - 1);

            deviations.withChild(newCommonLeafsMapEntryBuilder(IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME,
                context.findModule(lastComponent.getModule()).get())
                .build());
        }
        mapEntryBuilder.withChild(deviations.build());
    }

    /**
     * Mapping features of specific module.
     *
     * @param mapEntryBuilder mapEntryBuilder of parent for mapping children
     * @param features features of specific module
     */
    private static void addFeatureLeafList(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Collection<? extends FeatureDefinition> features) {
        final ListNodeBuilder<String, SystemLeafSetNode<String>> leafSetBuilder = Builders.<String>leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME));
        for (final FeatureDefinition feature : features) {
            leafSetBuilder.withChildValue(feature.getQName().getLocalName());
        }
        mapEntryBuilder.withChild(leafSetBuilder.build());
    }

    private static DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> newCommonLeafsMapEntryBuilder(
            final QName qname, final ModuleLike module) {
        final var name = module.getName();
        final var revision = module.getQNameModule().getRevision().map(Revision::toString).orElse("");
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(qname, Map.of(
                IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, name,
                IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, revision)))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, name))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, revision));
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolveFullNameFromNode;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.DeleteEntity;
import org.opendaylight.restconf.openapi.model.GetEntity;
import org.opendaylight.restconf.openapi.model.ParameterEntity;
import org.opendaylight.restconf.openapi.model.ParameterSchemaEntity;
import org.opendaylight.restconf.openapi.model.PatchEntity;
import org.opendaylight.restconf.openapi.model.PathEntity;
import org.opendaylight.restconf.openapi.model.PostEntity;
import org.opendaylight.restconf.openapi.model.PutEntity;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.DecimalTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int8TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint8TypeDefinition;

public final class PathsSteam extends InputStream {
    private final Iterator<? extends Module> iterator;
    private final JsonGenerator generator;
    private final OpenApiBodyWriter writer;
    private final ByteArrayOutputStream stream;
    private final EffectiveModelContext context;
    private final String deviceName;
    private final String urlPrefix;
    private final boolean isForSingleModule;
    private final boolean includeDataStore;

    private static final String OPERATIONS = "/rests/operations";
    private static final String DATA = "/rests/data";
    private static boolean hasRootPostLink = false;
    private static boolean toAddDataStorePaths = true;

    private Reader reader;
    private boolean eof;

    public PathsSteam(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream, final boolean isForSingleModule,
            final String deviceName, final String urlPrefix, final boolean includeDataStore) {
        iterator = context.getModules().iterator();
        this.generator = generator;
        this.writer = writer;
        this.stream = stream;
        this.context = context;
        this.isForSingleModule = isForSingleModule;
        this.deviceName = deviceName;
        this.urlPrefix = urlPrefix;
        this.includeDataStore = includeDataStore;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("paths");
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
        }

        var read = reader.read();
        while (read == -1) {
            if (iterator.hasNext()) {
                reader = new InputStreamReader(new PathStream(toPaths(iterator.next(), context, isForSingleModule,
                    deviceName, urlPrefix, includeDataStore), writer), StandardCharsets.UTF_8);
                read = reader.read();
                continue;
            }
            generator.writeEndObject();
            generator.flush();
            reader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8);
            stream.reset();
            eof = true;
            return reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }

    private static Deque<PathEntity> toPaths(final Module module, final EffectiveModelContext schemaContext,
            final boolean singleModule, final String deviceName, final String urlPrefix,
            final boolean includeDataStore) {
        final var result = new ArrayDeque<PathEntity>();
        if (includeDataStore && toAddDataStorePaths) {
            final var dataPath = DATA + urlPrefix;
            result.add(new PathEntity(dataPath, null, null, null,
                new GetEntity(null, deviceName, "data", null, null, false),
                null));
            final var operationsPath = OPERATIONS + urlPrefix;
            result.add(new PathEntity(operationsPath, null, null, null,
                new GetEntity(null, deviceName, "operations", null, null, false),
                null));
            toAddDataStorePaths = false;
        }
        // RPC operations (via post) - RPCs have their own path
        for (final var rpc : module.getRpcs()) {
            // TODO connect path with payload
            final var localName = rpc.getQName().getLocalName();
            final var post = new PostEntity(rpc, deviceName, module.getName(), new ArrayList<>(), localName, null);
            final var resolvedPath = OPERATIONS + urlPrefix + "/" + module.getName() + ":" + localName;
            final var entity = new PathEntity(resolvedPath, post, null, null, null, null);
            result.add(entity);
        }
        for (final var node : module.getChildNodes()) {
            final var moduleName = module.getName();
            final boolean isConfig = node.effectiveConfig().orElse(Boolean.TRUE);
            final var pathParams = new ArrayList<ParameterEntity>();

            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                if (isConfig && !hasRootPostLink && singleModule) {
                    final var resolvedPath = DATA;
                    hasRootPostLink = true;
                    result.add(processRootAndActionPathEntity(node, resolvedPath, pathParams, moduleName, "",
                        deviceName));
                }
                //process first node
                final var nodeLocalName = node.getQName().getLocalName();
                final var localName = moduleName + ":" + nodeLocalName;
                final var path = urlPrefix + "/" + processPath(node, pathParams, localName);
                final var resourcePath = DATA + path;
                final var fullName = resolveFullNameFromNode(node.getQName(), schemaContext);
                final var firstChild = getListOrContainerChildNode((DataNodeContainer) node);
                if (firstChild != null) {
                    result.add(processTopPathEntity(node, resourcePath, pathParams, moduleName, nodeLocalName, isConfig,
                        fullName, firstChild, deviceName));
                } else {
                    result.add(processDataPathEntity(node, resourcePath, pathParams, moduleName, nodeLocalName,
                        isConfig, fullName, deviceName));
                }
                processChildNode(node, pathParams, moduleName, result, path, nodeLocalName, isConfig, schemaContext,
                    deviceName);
            }
        }
        return result;
    }

    private static void processChildNode(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String moduleName, final Deque<PathEntity> result, final String path, final String refPath,
            final boolean isConfig, final EffectiveModelContext schemaContext, final String deviceName) {
        final var childNodes = ((DataNodeContainer) node).getChildNodes();
        for (final var childNode : childNodes) {
            final var childParams = new ArrayList<>(pathParams);
            final var newRefPath = refPath + "_" + childNode.getQName().getLocalName();
            if (childNode instanceof ActionNodeContainer actionContainer) {
                actionContainer.getActions().forEach(actionDef -> {
                    final var resourcePath = path + "/" + resolvePathArgumentsName(actionDef.getQName(),
                        childNode.getQName(), schemaContext);
                    final var childPath = OPERATIONS + resourcePath;
                    result.add(processRootAndActionPathEntity(actionDef, childPath, childParams, moduleName,
                        newRefPath, deviceName));
                });
            }
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final var localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final var resourcePath = path + "/" + processPath(childNode, childParams, localName);
                final var childPath = DATA + resourcePath;
                final var fullName = resolveFullNameFromNode(childNode.getQName(), schemaContext);
                final var newConfig = isConfig && childNode.effectiveConfig().orElse(Boolean.TRUE);
                if (childNode instanceof ContainerSchemaNode containerSchemaNode) {
                    final var firstChild = getListOrContainerChildNode(containerSchemaNode);
                    if (firstChild != null) {
                        result.add(processTopPathEntity(childNode, childPath, childParams, moduleName, newRefPath,
                            newConfig, fullName, firstChild, deviceName));
                    } else {
                        result.add(processDataPathEntity(childNode, childPath, childParams, moduleName, newRefPath,
                            newConfig, fullName, deviceName));
                    }
                } else {
                    result.add(processDataPathEntity(childNode, childPath, childParams, moduleName, newRefPath,
                        newConfig, fullName, deviceName));
                }
                processChildNode(childNode, childParams, moduleName, result, resourcePath, newRefPath, newConfig,
                    schemaContext, deviceName);
            }
        }
    }

    private static <T extends DataNodeContainer> DataSchemaNode getListOrContainerChildNode(final T node) {
        return node.getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }

    private static PathEntity processDataPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final String deviceName) {
        if (isConfig) {
            return new PathEntity(resourcePath, null,
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath, null, null, null,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false), null);
        }
    }

    private static PathEntity processTopPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final SchemaNode childNode, final String deviceName) {
        if (isConfig) {
            final var childNodeRefPath = refPath + "_" + childNode.getQName().getLocalName();
            return new PathEntity(resourcePath,
                new PostEntity(childNode, deviceName, moduleName, pathParams, childNodeRefPath, node),
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath, null, null, null,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false), null);
        }
    }

    private static PathEntity processRootAndActionPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final String deviceName) {
        return new PathEntity(resourcePath,
            new PostEntity(node, deviceName, moduleName, pathParams, refPath, null),
            null, null, null, null);
    }

    private static String processPath(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String localName) {
        final StringBuilder path = new StringBuilder();
        path.append(localName);
        final Set<String> parameters = pathParams.stream()
            .map(ParameterEntity::name)
            .collect(Collectors.toSet());

        if (node instanceof ListSchemaNode listSchemaNode) {
            String prefix = "=";
            int discriminator = 1;
            for (final QName listKey : listSchemaNode.getKeyDefinition()) {
                final String keyName = listKey.getLocalName();
                String paramName = keyName;
                while (!parameters.add(paramName)) {
                    paramName = keyName + discriminator;
                    discriminator++;
                }

                final String pathParamIdentifier = prefix + "{" + paramName + "}";
                prefix = ",";
                path.append(pathParamIdentifier);

                final String description = listSchemaNode.findDataChildByName(listKey)
                    .flatMap(DataSchemaNode::getDescription).orElse(null);

                pathParams.add(new ParameterEntity(paramName, "path", true,
                    new ParameterSchemaEntity(getAllowedType(listSchemaNode, listKey), null), description));
            }
        }
        return path.toString();
    }

    private static String getAllowedType(final ListSchemaNode list, final QName key) {
        final var keyType = ((LeafSchemaNode) list.getDataChildByName(key)).getType();

        // see: https://datatracker.ietf.org/doc/html/rfc7950#section-4.2.4
        // see: https://swagger.io/docs/specification/data-models/data-types/
        // TODO: Java 21 use pattern matching for switch
        if (keyType instanceof Int8TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int16TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int32TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int64TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint8TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint16TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint32TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint64TypeDefinition) {
            return "integer";
        }

        if (keyType instanceof DecimalTypeDefinition) {
            return "number";
        }

        if (keyType instanceof BooleanTypeDefinition) {
            return "boolean";
        }

        return "string";
    }
}

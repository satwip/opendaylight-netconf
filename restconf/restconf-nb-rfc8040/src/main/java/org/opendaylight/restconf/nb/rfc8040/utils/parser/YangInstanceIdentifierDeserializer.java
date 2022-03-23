/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.IdentityEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class YangInstanceIdentifierDeserializer {
    private final @NonNull EffectiveModelContext schemaContext;
    private final @NonNull ApiPath apiPath;

    private YangInstanceIdentifierDeserializer(final EffectiveModelContext schemaContext, final ApiPath apiPath) {
        this.schemaContext = requireNonNull(schemaContext);
        this.apiPath = requireNonNull(apiPath);
    }

    /**
     * Method to create {@link List} from {@link PathArgument} which are parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext for validate of parsing path arguments
     * @param data path to data, in URL string form
     * @return {@link Iterable} of {@link PathArgument}
     * @throws RestconfDocumentedException the path is not valid
     */
    public static List<PathArgument> create(final EffectiveModelContext schemaContext, final String data) {
        final ApiPath path;
        try {
            path = ApiPath.parse(requireNonNull(data));
        } catch (ParseException e) {
            throw new RestconfDocumentedException("Invalid path '" + data + "' at offset " + e.getErrorOffset(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e);
        }
        return create(schemaContext, path);
    }

    public static List<PathArgument> create(final EffectiveModelContext schemaContext, final ApiPath path) {
        return new YangInstanceIdentifierDeserializer(schemaContext, path).parse();
    }

    // FIXME: NETCONF-818: this method really needs to report an Inference and optionally a YangInstanceIdentifier
    // - we need the inference for discerning the correct context
    // - RPCs do not have a YangInstanceIdentifier
    // - Actions always have a YangInstanceIdentifier, but it points to their parent
    // - we need to discern the cases RPC invocation, Action invocation and data tree access quickly
    //
    // All of this really is an utter mess because we end up calling into this code from various places which,
    // for example, should not allow RPCs to be valid targets
    private ImmutableList<PathArgument> parse() {
        final var it = apiPath.steps().iterator();
        if (!it.hasNext()) {
            return ImmutableList.of();
        }

        // First step is somewhat special:
        // - it has to contain a module qualifier
        // - it has to consider RPCs, for which we need SchemaContext
        //
        // We therefore peel that first iteration here and not worry about those details in further iterations
        var step = it.next();
        final var firstModule = RestconfDocumentedException.throwIfNull(step.module(),
            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
            "First member must use namespace-qualified form, '%s' does not", step.identifier());
        var namespace = resolveNamespace(firstModule);
        var qname = step.identifier().bindTo(namespace);

        // We go through more modern APIs here to get this special out of the way quickly
        final var optRpc = schemaContext.findModuleStatement(namespace).orElseThrow()
            .findSchemaTreeNode(RpcEffectiveStatement.class, qname);
        if (optRpc.isPresent()) {
            // We have found an RPC match,
            if (it.hasNext()) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must be the only step present", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
            if (step instanceof ListInstance) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must not contain key values", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }

            // Legacy behavior: RPCs do not really have a YangInstanceIdentifier, but the rest of the code expects it
            return ImmutableList.of(new NodeIdentifier(qname));
        }

        final var path = new ArrayList<PathArgument>();
        var parentNode = DataSchemaContextTree.from(schemaContext).getRoot();
        while (true) {
            final var parentSchema = parentNode.getDataSchemaNode();
            if (parentSchema instanceof ActionNodeContainer) {
                if (((ActionNodeContainer) parentSchema).findAction(qname).isPresent()) {
                    if (it.hasNext()) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not continue past it", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }
                    if (step instanceof ListInstance) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not contain key values",
                            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }

                    // Legacy behavior: Action's path should not include its path, but the rest of the code expects it
                    path.add(new NodeIdentifier(qname));
                    break;
                }
            }

            // Resolve the child step with respect to data schema tree
            final var found = RestconfDocumentedException.throwIfNull(parentNode.getChild(qname),
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Schema for '%s' not found", qname);

            // Now add all mixins encountered to the path
            var childNode = found;
            while (childNode.isMixin()) {
                path.add(childNode.getIdentifier());
                childNode = verifyNotNull(childNode.getChild(qname),
                    "Mixin %s is missing child for %s while resolving %s", childNode, qname, found);
            }

            final PathArgument pathArg;
            if (step instanceof ListInstance) {
                final var values = ((ListInstance) step).keyValues();
                final var schema = childNode.getDataSchemaNode();
                pathArg = schema instanceof ListSchemaNode
                    ? prepareNodeWithPredicates(qname, (ListSchemaNode) schema, values)
                        : prepareNodeWithValue(qname, schema, values);
            } else {
                RestconfDocumentedException.throwIf(childNode.isKeyedEntry(),
                    ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                    "Entry '%s' requires key or value predicate to be present.", qname);
                pathArg = childNode.getIdentifier();
            }

            path.add(pathArg);

            if (!it.hasNext()) {
                break;
            }

            parentNode = childNode;
            step = it.next();
            final var module = step.module();
            if (module != null) {
                namespace = resolveNamespace(module);
            }

            qname = step.identifier().bindTo(namespace);
        }

        return ImmutableList.copyOf(path);
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final QName qname,
            final @NonNull ListSchemaNode schema, final List<@NonNull String> keyValues) {
        final var keyDef = schema.getKeyDefinition();
        final var keySize = keyDef.size();
        final var varSize = keyValues.size();
        if (keySize != varSize) {
            throw new RestconfDocumentedException(
                "Schema for " + qname + " requires " + keySize + " key values, " + varSize + " supplied",
                ErrorType.PROTOCOL, keySize > varSize ? ErrorTag.MISSING_ATTRIBUTE : ErrorTag.UNKNOWN_ATTRIBUTE);
        }

        final var values = ImmutableMap.<QName, Object>builderWithExpectedSize(keySize);
        for (int i = 0; i < keySize; ++i) {
            final QName keyName = keyDef.get(i);
            values.put(keyName, prepareValueByType(schema.getDataChildByName(keyName), keyValues.get(i)));
        }

        return NodeIdentifierWithPredicates.of(qname, values.build());
    }

    private Object prepareValueByType(final DataSchemaNode schemaNode, final @NonNull String value) {

        TypeDefinition<? extends TypeDefinition<?>> typedef;
        if (schemaNode instanceof LeafListSchemaNode) {
            typedef = ((LeafListSchemaNode) schemaNode).getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaInferenceStack.ofInstantiatedPath(schemaContext, schemaNode.getPath())
                .resolveLeafref((LeafrefTypeDefinition) baseType);
        }

        if (typedef instanceof IdentityrefTypeDefinition) {
            return toIdentityrefQName(value, schemaNode);
        }
        try {
            return RestCodec.from(typedef, null, schemaContext).deserialize(value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid value '" + value + "' for " + schemaNode.getQName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    private NodeWithValue<?> prepareNodeWithValue(final QName qname, final DataSchemaNode schema,
            final List<String> keyValues) {
        // TODO: qname should be always equal to schema.getQName(), right?
        return new NodeWithValue<>(qname, prepareValueByType(schema,
            // FIXME: ahem: we probably want to do something differently here
            keyValues.get(0)));
    }

    private QName toIdentityrefQName(final String value, final DataSchemaNode schemaNode) {
        final QNameModule namespace;
        final String localName;
        final int firstColon = value.indexOf(':');
        if (firstColon != -1) {
            namespace = resolveNamespace(value.substring(0, firstColon));
            localName = value.substring(firstColon + 1);
        } else {
            namespace = schemaNode.getQName().getModule();
            localName = value;
        }

        return schemaContext.getModuleStatement(namespace)
            .streamEffectiveSubstatements(IdentityEffectiveStatement.class)
            .map(IdentityEffectiveStatement::argument)
            .filter(qname -> localName.equals(qname.getLocalName()))
            .findFirst()
            .orElseThrow(() -> new RestconfDocumentedException(
                "No identity found for '" + localName + "' in namespace " + namespace,
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) {
        final var modules = schemaContext.findModules(moduleName);
        RestconfDocumentedException.throwIf(modules.isEmpty(), ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
            "Failed to lookup for module with name '%s'.", moduleName);
        return modules.iterator().next().getQNameModule();
    }
}

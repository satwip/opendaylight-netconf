/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Serializer for {@link YangInstanceIdentifier} to {@link String} for restconf.
 */
public final class YangInstanceIdentifierSerializer {
    private final @NonNull DatabindContext databind;

    public YangInstanceIdentifierSerializer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    /**
     * Method to create String from {@link Iterable} of {@link PathArgument} which are parsing from data with the help
     * of an {@link EffectiveModelContext}.
     *
     * @param path path to data
     * @return {@link String}
     */
    public String serializePath(final YangInstanceIdentifier path) {
        return new ApiPathNormalizer(databind).canonicalize(path).toString();
    }

    private String preparePath(final YangInstanceIdentifier path, final boolean isForValue) {
        final var current = databind.schemaTree().getRoot();
        final var variables = new MainVarsWrapper(current);
        final var sb = new StringBuilder();
        QNameModule parentModule = null;
        for (var arg : path.getPathArguments()) {
            // get module of the parent
            final var currentContext = variables.getCurrent();

            if (!(currentContext instanceof PathMixin)) {
                parentModule = currentContext.dataSchemaNode().getQName().getModule();
            }

            final var childContext = currentContext instanceof DataSchemaContext.Composite composite
                ? composite.childByArg(arg) : null;
            if (childContext == null) {
                throw new RestconfDocumentedException(
                    "Invalid input '%s': schema for argument '%s' (after '%s') not found".formatted(path, arg, sb),
                    ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT);
            }

            variables.setCurrent(childContext);
            if (childContext instanceof PathMixin) {
                continue;
            }

            // append namespace before every node which is defined in other module than its parent
            // condition is satisfied also for the first path argument
            if (!arg.getNodeType().getModule().equals(parentModule)) {
                // append slash if it is not the first path argument
                if (sb.length() > 0) {
                    sb.append('/');
                }

                sb.append(prefixForNamespace(arg.getNodeType())).append(':');
            } else {
                sb.append('/');
            }

            sb.append(arg.getNodeType().getLocalName());
            if (arg instanceof NodeIdentifierWithPredicates withPredicates) {
                prepareNodeWithPredicates(sb, withPredicates.entrySet(), isForValue);
            } else if (arg instanceof NodeWithValue<?> withValue) {
                prepareNode(sb, withValue.getValue());
            }
        }
        return sb.toString();
    }

    private static void prepareNode(final StringBuilder path, final Object entry) {
        path.append('=');
        // FIXME: this is quite fishy
        final var str = String.valueOf(entry);
        path.append(ApiPath.PERCENT_ESCAPER.escape(str));
    }

    private void prepareNodeWithPredicates(final StringBuilder sb, final Set<Map.Entry<QName, Object>> entries,
            final boolean isForValue) {
        final var iterator = entries.iterator();
        if (!isForValue && iterator.hasNext()) {
            sb.append('=');
        }
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final var entryValue = entry.getValue();
            if (entryValue instanceof YangInstanceIdentifier yii) {
                final var value = preparePath(yii, true);
                sb.append(ApiPath.PERCENT_ESCAPER.escape(value));
            } else if (isForValue) {
                sb.append('[').append(entry.getKey().getLocalName()).append('=').append(entryValue)
                    .append(']');
            } else {
                sb.append(ApiPath.PERCENT_ESCAPER.escape(entryValue.toString()));
            }
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
    }


    /**
     * Create prefix of namespace from {@link QName}.
     *
     * @param qname {@link QName}
     * @return {@link String}
     */
    private String prefixForNamespace(final QName qname) {
        return databind.modelContext().findModuleStatement(qname.getModule()).orElseThrow().argument().getLocalName();
    }

    private static final class MainVarsWrapper {
        private DataSchemaContext current;

        MainVarsWrapper(final DataSchemaContext current) {
            setCurrent(current);
        }

        public DataSchemaContext getCurrent() {
            return current;
        }

        public void setCurrent(final DataSchemaContext current) {
            this.current = current;
        }
    }
}

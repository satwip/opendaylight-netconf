/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3.2">RFC8040</a>.
 */
enum OperationsContent {
    JSON("{ \"ietf-restconf:operations\" : { } }") {
        @Override
        String createBody(final List<Entry<String, List<String>>> rpcsByPrefix) {
            final var sb = new StringBuilder("{\n"
                + "  \"ietf-restconf:operations\" : {\n");
            var entryIt = rpcsByPrefix.iterator();
            var entry = entryIt.next();
            var nameIt = entry.getValue().iterator();
            while (true) {
                sb.append("    \"").append(entry.getKey()).append(':').append(nameIt.next()).append("\": [null]");
                if (nameIt.hasNext()) {
                    sb.append(",\n");
                    continue;
                }

                if (entryIt.hasNext()) {
                    sb.append(",\n");
                    entry = entryIt.next();
                    nameIt = entry.getValue().iterator();
                    continue;
                }

                break;
            }

            return sb.append("\n  }\n}").toString();
        }

        @Override
        String prefix(final ModuleEffectiveStatement module) {
            return module.argument().getLocalName();
        }
    },

    XML("<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"/>") {
        @Override
        String createBody(final List<Entry<String, List<String>>> rpcsByPrefix) {
            // Header with namespace declarations for each module
            final var sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"");
            for (int i = 0; i < rpcsByPrefix.size(); ++i) {
                final var prefix = "ns" + i;
                sb.append("\n            xmlns:").append(prefix).append("=\"").append(rpcsByPrefix.get(i).getKey())
                    .append("\"");
            }
            sb.append(" >");

            // Second pass: emit all leaves
            for (int i = 0; i < rpcsByPrefix.size(); ++i) {
                final var prefix = "ns" + i;
                for (var localName : rpcsByPrefix.get(i).getValue()) {
                    sb.append("\n  <").append(prefix).append(':').append(localName).append("/>");
                }
            }

            return sb.append("\n</operations>").toString();
        }

        @Override
        String prefix(final ModuleEffectiveStatement module) {
            return module.localQNameModule().getNamespace().toString();
        }
    };

    private final @NonNull String emptyBody;

    OperationsContent(final String emptyBody) {
        this.emptyBody = requireNonNull(emptyBody);
    }

    /**
     * Return the content for a particular {@link EffectiveModelContext}.
     *
     * @param context Context to use
     * @return Content of HTTP GET operation as a String
     */
    public final @NonNull String bodyFor(final @Nullable EffectiveModelContext context) {
        if (context == null) {
            return emptyBody;
        }
        final var modules = context.getModuleStatements();
        if (modules.isEmpty()) {
            return emptyBody;
        }


        // Now extract RPC names for each module with highest revision. This needed so we expose the right set of RPCs,
        // as we always pick the latest revision to resolve prefix (or module name)
        final var moduleRpcs = new HashMap<String, List<String>>();
        for (var module : modules.values()) {
            final var namespace = module.localQNameModule().getNamespace();
            if (moduleRpcs.containsKey(namespace)) {
                continue;
            }
            final var stmt = context.findModuleStatements(namespace).stream().findFirst().get();

            final var rpcNames = stmt.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                    .map(rpc -> rpc.argument().getLocalName())
                    .toList();
            if (!rpcNames.isEmpty()) {
                moduleRpcs.put(prefix(stmt), rpcNames);
            }
        }

        if (moduleRpcs.isEmpty()) {
            // No RPCs, return empty content
            return emptyBody;
        }

        // Ensure stability: sort by prefix
        final var rpcsList = new ArrayList<Entry<String, List<String>>>();
        rpcsList.addAll(moduleRpcs.entrySet().stream().toList());
        rpcsList.sort(Comparator.comparing(Entry::getKey));

        return modules.isEmpty() ? emptyBody : createBody(rpcsList);
    }

    abstract @NonNull String createBody(List<Entry<String, List<String>>> rpcsByPrefix);

    abstract @NonNull String prefix(ModuleEffectiveStatement module);
}

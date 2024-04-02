/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

public abstract class BaseYangOpenApiGenerator {
    private static final String CONTROLLER_RESOURCE_NAME = "Controller";
    public static final List<Map<String, List<String>>> SECURITY = List.of(Map.of("basicAuth", List.of()));

    private final DOMSchemaService schemaService;

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this.schemaService = requireNonNull(schemaService);
    }

    public OpenApiInputStream getControllerModulesDoc(final UriInfo uriInfo, final Integer offset, final Integer limit)
            throws IOException {
        final var context = requireNonNull(schemaService.getGlobalContext());
        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = "Controller modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modulesWithoutDuplications = getModulesWithoutDuplications(context);
        final var portionOfModels = getPortionOfModels(modulesWithoutDuplications, requireNonNullElse(offset, 0),
            requireNonNullElse(limit, 0));
        return new OpenApiInputStream(context, title, url, SECURITY, CONTROLLER_RESOURCE_NAME, "", false, false,
            portionOfModels, basePath);
    }

    public OpenApiInputStream getApiDeclaration(final String module, final String revision, final UriInfo uriInfo)
            throws IOException {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getApiDeclaration(module, revision, uriInfo, schemaContext, "", CONTROLLER_RESOURCE_NAME);
    }

    public OpenApiInputStream getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext schemaContext, final String urlPrefix, final @NonNull String deviceName)
            throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final var module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = module.getName();
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modules = List.of(module);
        return new OpenApiInputStream(schemaContext, title, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath);
    }

    public String createHostFromUriInfo(final UriInfo uriInfo) {
        String portPart = "";
        final int port = uriInfo.getBaseUri().getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uriInfo.getBaseUri().getHost() + portPart;
    }

    public String createSchemaFromUriInfo(final UriInfo uriInfo) {
        return uriInfo.getBaseUri().getScheme();
    }

    public abstract String getBasePath();

    public static Set<Module> getModulesWithoutDuplications(final @NonNull EffectiveModelContext schemaContext) {
        return new LinkedHashSet<>(schemaContext.getModules()
            .stream()
            .collect(Collectors.toMap(
                Module::getName,
                Function.identity(),
                (module1, module2) -> Revision.compare(
                    module1.getRevision(), module2.getRevision()) > 0 ? module1 : module2,
                LinkedHashMap::new))
            .values());
    }

    public static Collection<? extends Module> getPortionOfModels(final Set<Module> modulesWithoutDuplications,
            final @NonNull Integer offset, final @NonNull Integer limit) {
        if (offset != 0 || limit != 0) {
            final var augmentingModules = new ArrayList<Module>();
            final var modules = modulesList(modulesWithoutDuplications, augmentingModules);
            if (offset > modules.size() || offset < 0 || limit < 0) {
                return List.of();
            } else {
                final var end = limit == 0 ? modules.size() : Math.min(modules.size(), offset + limit);
                final var portionOfModules = modules.subList(offset, end);
                portionOfModules.addAll(augmentingModules);
                return portionOfModules;
            }
        }
        return modulesWithoutDuplications;
    }

    private static List<Module> modulesList(final Set<Module> modulesWithoutDuplications,
            final List<Module> augmentingModules) {
        return modulesWithoutDuplications
            .stream()
            .filter(module -> {
                if (containsDataOrOperation(module)) {
                    return true;
                } else {
                    augmentingModules.add(module);
                    return false;
                }
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean containsDataOrOperation(final Module module) {
        if (!module.getRpcs().isEmpty()) {
            return true;
        }
        return module.getChildNodes()
            .stream()
            .anyMatch(node -> node instanceof ContainerSchemaNode || node instanceof ListSchemaNode);
    }
}

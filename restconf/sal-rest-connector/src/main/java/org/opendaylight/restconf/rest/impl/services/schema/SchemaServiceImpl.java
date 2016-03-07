/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services.schema;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import org.opendaylight.netconf.md.sal.rest.common.RestconfValidationUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.schema.SchemaService;
import org.opendaylight.restconf.utils.RestSchemaControllerUtil;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class SchemaServiceImpl implements SchemaService {

    private final RestSchemaController restSchemaController;

    private static final Splitter SLASH_SPLITTER = Splitter.on("/");
    private static final String MOUNT_ARG = RestSchemaControllerUtil.MOUNT;

    public SchemaServiceImpl(final RestSchemaController restSchemaController) {
        this.restSchemaController = restSchemaController;
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModule) {
        final SchemaContext schemaContext;
        final Iterable<String> pathComponents = SLASH_SPLITTER.split(mountAndModule);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, MOUNT_ARG)) {
            schemaContext = this.restSchemaController.getGlobalSchema();
        } else {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();
                // It is argument, not last element.
                if (pathBuilder.length() != 0) {
                    pathBuilder.append("/");
                }
                pathBuilder.append(current);
                if (MOUNT_ARG.equals(current)) {
                    // We stop right at mountpoint, last two arguments should
                    // be module name and revision
                    break;
                }
            }
            schemaContext = getMountSchemaContext(pathBuilder.toString());

        }

        RestconfValidationUtils.checkDocumentedError(componentIter.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Module name must be supplied.");
        final String moduleName = componentIter.next();
        RestconfValidationUtils.checkDocumentedError(componentIter.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Revision date must be supplied.");
        final String revisionString = componentIter.next();
        return getExportUsingNameAndRevision(schemaContext, moduleName, revisionString);
    }

    private SchemaExportContext getExportUsingNameAndRevision(final SchemaContext schemaContext,
            final String moduleName, final String revisionStr) {
        try {
            final Date revision = SimpleDateFormatUtil.getRevisionFormat().parse(revisionStr);
            final Module module = schemaContext.findModuleByName(moduleName, revision);
            return new SchemaExportContext(schemaContext,
                    RestconfValidationUtils.checkNotNullDocumented(module, moduleName));
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Supplied revision is not in expected date format YYYY-mm-dd", e);
        }
    }

    private SchemaContext getMountSchemaContext(final String identifier) {
        final InstanceIdentifierContext<?> mountContext = this.restSchemaController.toMountPointIdentifier(identifier);
        return mountContext.getSchemaContext();
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameter {
    public static @Nonnull List<QName> parseFieldsParameter(@Nonnull final String fields,
                                                            @Nonnull final String defaultPrefix,
                                                            @Nonnull final SchemaContext context) {
        final List<QName> result = new ArrayList<>();

        String prefix = defaultPrefix;
        int j = 0;

        for (int i = 0; i < fields.length(); i++) {
            if (CharMatcher.noneOf(":/();").matches(fields.charAt(i))) {
                continue;
            }

            if (CharMatcher.is(':').matches(fields.charAt(i))) {
                prefix = fields.substring(j, i);
            } else if (CharMatcher.anyOf("/();").matches(fields.charAt(i))) {
                final Module module = context.findModuleByName(prefix, null);
                result.add(QName.create(module.getNamespace().toString(), fields.substring(j, i)));
            }

            j = i + 1;
        }

        if (j != fields.length()) {
            final Module module = context.findModuleByName(prefix, null);
            result.add(QName.create(module.getNamespace().toString(), fields.substring(j)));
        }

        return result;
    }
}

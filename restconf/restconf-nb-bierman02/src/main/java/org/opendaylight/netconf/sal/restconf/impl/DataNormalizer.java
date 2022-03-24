/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

class DataNormalizer {
    private final DataNormalizationOperation<?> operation;

    DataNormalizer(final EffectiveModelContext ctx) {
        operation = DataNormalizationOperation.from(ctx);
    }

    YangInstanceIdentifier toNormalized(final YangInstanceIdentifier legacy) {
        List<PathArgument> normalizedArgs = new ArrayList<>();

        DataNormalizationOperation<?> currentOp = operation;
        Iterator<PathArgument> arguments = legacy.getPathArguments().iterator();

        try {
            while (arguments.hasNext()) {
                PathArgument legacyArg = arguments.next();
                currentOp = currentOp.getChild(legacyArg);
                checkArgument(currentOp != null,
                        "Legacy Instance Identifier %s is not correct. Normalized Instance Identifier so far %s",
                        legacy, normalizedArgs);
                while (currentOp.isMixin()) {
                    normalizedArgs.add(currentOp.getIdentifier());
                    currentOp = currentOp.getChild(legacyArg.getNodeType());
                }
                normalizedArgs.add(legacyArg);
            }
        } catch (DataNormalizationException e) {
            throw new IllegalArgumentException("Failed to normalize path " + legacy, e);
        }

        return YangInstanceIdentifier.create(normalizedArgs);
    }

    DataNormalizationOperation<?> getOperation(final YangInstanceIdentifier legacy)
            throws DataNormalizationException {
        DataNormalizationOperation<?> currentOp = operation;

        for (PathArgument pathArgument : legacy.getPathArguments()) {
            currentOp = currentOp.getChild(pathArgument);
        }
        return currentOp;
    }

    YangInstanceIdentifier toLegacy(final YangInstanceIdentifier normalized) throws DataNormalizationException {
        ImmutableList.Builder<PathArgument> legacyArgs = ImmutableList.builder();
        DataNormalizationOperation<?> currentOp = operation;
        for (PathArgument normalizedArg : normalized.getPathArguments()) {
            currentOp = currentOp.getChild(normalizedArg);
            if (!currentOp.isMixin()) {
                legacyArgs.add(normalizedArg);
            }
        }
        return YangInstanceIdentifier.create(legacyArgs.build());
    }
}

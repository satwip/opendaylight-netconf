/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TransactionUtil {

    public static void ensureParentsByMerge(final YangInstanceIdentifier path, final SchemaContext schemaContext,
            final DOMDataWriteTransaction writeTx) {
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = path.getPathArguments().iterator();

        while (it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }
            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }
        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        Preconditions.checkArgument(rootNormalizedPath != null, "Empty path received");

        final NormalizedNode<?, ?> parentStructure = ImmutableNodes.fromInstanceId(schemaContext,
                YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, rootNormalizedPath, parentStructure);
    }
}

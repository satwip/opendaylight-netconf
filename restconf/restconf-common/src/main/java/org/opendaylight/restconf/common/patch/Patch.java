/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@NonNullByDefault
public record Patch(String patchId, ImmutableList<Patch.Edit> edits) {
    public record Edit(
            String editId,
            Operation operation,
            YangInstanceIdentifier target,
            @Nullable NormalizedNode node) {
        public Edit {
            requireNonNull(editId);
            requireNonNull(operation);
            requireNonNull(target);
        }

        /**
         * Constructor to create PatchEntity for Patch operations which do not allow value leaf representing data to be
         * present. {@code node} is set to {@code null} meaning that data are not allowed for edit operation.
         *
         * @param editId Id of Patch edit
         * @param operation Patch edit operation
         * @param targetNode Target node for Patch edit operation
         */
        public Edit(final String editId, final Operation operation, final YangInstanceIdentifier targetNode) {
            this(editId, operation, targetNode, null);
        }
    }

    public Patch {
        requireNonNull(patchId);
        requireNonNull(edits);
    }

    public Patch(final String patchId, final List<Edit> edits) {
        this(patchId, ImmutableList.copyOf(edits));
    }
}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Holds URLs with YANG schema resources for all yang modules reported in
 * ietf-netconf-yang-library/modules-state/modules node.
 */
public final class LibraryModulesSchemas implements NetconfDeviceSchemas {
    private final ImmutableMap<QName, URL> availableModels;

    public LibraryModulesSchemas(final ImmutableMap<QName, URL> availableModels) {
        this.availableModels = requireNonNull(availableModels);
    }

    public Map<SourceIdentifier, URL> getAvailableModels() {
        final Map<SourceIdentifier, URL> result = new HashMap<>();
        for (final Entry<QName, URL> entry : availableModels.entrySet()) {
            final SourceIdentifier sId = new SourceIdentifier(entry.getKey().getLocalName(),
                entry.getKey().getRevision().map(Revision::toString).orElse(null));
            result.put(sId, entry.getValue());
        }

        return result;
    }

    @Override
    public Set<QName> getAvailableYangSchemasQNames() {
        return null;
    }
}

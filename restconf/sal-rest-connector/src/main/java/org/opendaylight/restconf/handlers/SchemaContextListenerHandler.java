/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

interface SchemaContextListenerHandler extends Handler<SchemaContext>, SchemaContextListener {

    /**
     * Return server-specific identifier representing the current set of modules
     * and submodules
     *
     * @return identifier
     */
    String getActualModuleSetId();
}

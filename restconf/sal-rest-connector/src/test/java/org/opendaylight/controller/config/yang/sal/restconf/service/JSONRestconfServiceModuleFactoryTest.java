/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.sal.restconf.service;

import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class JSONRestconfServiceModuleFactoryTest {

    @Test
    public void jrsmfInit1Test() {
        final JSONRestconfServiceModuleFactory jrsmf = new JSONRestconfServiceModuleFactory();
        assertNotNull(jrsmf);
    }
}

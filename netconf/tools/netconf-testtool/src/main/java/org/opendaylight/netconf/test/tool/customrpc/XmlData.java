/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.customrpc;

import javax.xml.bind.annotation.XmlAnyElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XmlData {
    @XmlAnyElement
    private Element data;

    Document getData() {
        return data.getOwnerDocument();
    }

}

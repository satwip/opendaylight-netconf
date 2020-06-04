/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;

public interface NetconfDOMDataBrokerOperations extends DOMDataBroker, NetconfDataBrokerOperations {

    NetconfOperationDOMTransactionChain createNetconfTransactionChain(DOMTransactionChainListener listener);
}

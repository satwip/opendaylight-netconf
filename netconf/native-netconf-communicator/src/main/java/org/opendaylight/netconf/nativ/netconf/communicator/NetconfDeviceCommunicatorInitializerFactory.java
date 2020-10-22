/*
 * Copyright (c) 2020 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import org.opendaylight.netconf.client.NetconfClientDispatcher;

public interface NetconfDeviceCommunicatorInitializerFactory {

    NetconfDeviceCommunicatorFactory init(NetconfClientDispatcher netconfClientDispatcher);
}

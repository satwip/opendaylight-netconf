/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;

public interface NetconfWriteTransaction extends DOMDataWriteTransaction {
    CheckedFuture<Void, DataValidationFailedException> validate();
}

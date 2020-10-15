/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx.fields;

import static org.mockito.Mockito.verify;

import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.netconf.api.dom.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.TxTestUtils;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class FieldsAwareReadWriteTxTest {

    @Mock
    private NetconfDOMFieldsReadTransaction delegateReadTx;
    @Mock
    private DOMDataTreeWriteTransaction delegateWriteTx;

    @Test
    public void testReadWithFields() {
        final FieldsAwareReadWriteTx tx = new FieldsAwareReadWriteTx(delegateReadTx, delegateWriteTx);
        tx.read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(),
                Collections.singletonList(YangInstanceIdentifier.empty()));
        verify(delegateReadTx).read(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(),
                Collections.singletonList(YangInstanceIdentifier.empty()));
    }
}
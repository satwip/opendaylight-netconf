/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public final class XmlPatchBody extends PatchBody {
    public XmlPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final Inference inference, final YangInstanceIdentifier target,
            final InputStream inputStream) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
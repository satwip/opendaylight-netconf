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
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public abstract sealed class PutBody extends AbstractBody permits JsonPutBody, XmlPutBody {
    PutBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Stream the body into a {@link NormalizedNodeStreamWriter}.
     *
     * @param inference An {@link Inference} the statement corresponding to the body
     * @param writer Target writer
     * @throws IOException when an I/O error occurs
     */
    // TODO: pass down DatabindContext corresponding to inference
    public final void streamTo(final Inference inference, final NormalizedNodeStreamWriter writer) throws IOException {
        streamTo(acquireStream(), inference, writer);
    }

    abstract void streamTo(InputStream inputStream, Inference inference, NormalizedNodeStreamWriter writer)
        throws IOException;
}
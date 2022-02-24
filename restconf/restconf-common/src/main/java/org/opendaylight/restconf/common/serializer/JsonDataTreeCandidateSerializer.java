/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.serializer;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collection;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class JsonDataTreeCandidateSerializer extends AbstractWebsocketSerializer<IOException> {
    private final JSONCodecFactorySupplier codecSupplier;
    private final JsonWriter jsonWriter;

    public JsonDataTreeCandidateSerializer(final EffectiveModelContext context,
            final JSONCodecFactorySupplier codecSupplier, final JsonWriter jsonWriter) {
        super(context);
        this.codecSupplier = requireNonNull(codecSupplier);
        this.jsonWriter = requireNonNull(jsonWriter);
    }

    @Override
    void serializeData(final EffectiveModelContext context, final SchemaPath schemaPath,
            final Collection<PathArgument> dataPath, final DataTreeCandidateNode candidate, final boolean skipData)
                throws IOException {
        this.emptyDataChangedEvent = false;
        NormalizedNodeStreamWriter nestedWriter = JSONNormalizedNodeStreamWriter.createNestedWriter(
            codecSupplier.getShared(context), schemaPath.getParent(), null, jsonWriter);
        jsonWriter.beginObject();
        serializePath(dataPath);

        if (!skipData && candidate.getDataAfter().isPresent()) {
            jsonWriter.name("data").beginObject();
            NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(nestedWriter);
            nodeWriter.write(candidate.getDataAfter().get());
            nodeWriter.flush();

            // end data
            jsonWriter.endObject();
        }

        serializeOperation(candidate);
        jsonWriter.endObject();
    }

    @Override
    void serializeOperation(final DataTreeCandidateNode candidate)
            throws IOException {
        jsonWriter.name("operation").value(modificationTypeToOperation(candidate, candidate.getModificationType()));
    }

    @Override
    void serializePath(final Collection<YangInstanceIdentifier.PathArgument> pathArguments)
            throws IOException {
        jsonWriter.name("path").value(convertPath(pathArguments));
    }
}

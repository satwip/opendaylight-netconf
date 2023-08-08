/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

abstract class AbstractResourceBodyTest extends AbstractBodyReaderTest {
    static final NodeIdentifier CONT_NID = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
    static final NodeIdentifier CONT1_NID = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont1"));
    static final QName LST11= QName.create("augment:module", "2014-01-17", "lst11");
    static final QName KEYVALUE111 = QName.create(LST11, "keyvalue111");
    static final QName KEYVALUE112 = QName.create(LST11, "keyvalue112");
    static final QName LF111 = QName.create("augment:augment:module", "2014-01-17", "lf111");
    static final NodeIdentifier LF112_NID = new NodeIdentifier(QName.create(LF111, "lf112"));

    private final Function<InputStream, ResourceBody> bodyConstructor;
    private final EffectiveModelContext modelContext;

    AbstractResourceBodyTest(final Function<InputStream, ResourceBody> bodyConstructor,
            final EffectiveModelContext modelContext) {
        this.bodyConstructor = requireNonNull(bodyConstructor);
        this.modelContext = requireNonNull(modelContext);
    }

    // FIXME: migrate callers to use string literals
    @Deprecated
    final @NonNull NormalizedNode parseResource(final String uriPath, final String resourceName) throws IOException {
        return parse(uriPath, AbstractResourceBodyTest.class.getResourceAsStream(resourceName));
    }

    final @NonNull NormalizedNode parse(final String uriPath, final String patchBody) throws IOException {
        return parse(uriPath, stringInputStream(patchBody));
    }

    private @NonNull NormalizedNode parse(final String uriPath, final InputStream patchBody) throws IOException {
        try (var body = bodyConstructor.apply(patchBody)) {
            return body.toNormalizedNode(ParserIdentifier.toInstanceIdentifier(uriPath, modelContext, mountPointService)
                .inference());
        }
    }
}

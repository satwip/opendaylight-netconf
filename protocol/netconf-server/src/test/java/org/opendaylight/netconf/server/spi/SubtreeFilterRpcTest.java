/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;

public class SubtreeFilterRpcTest {

    public static List<Arguments> data() {
        return List.of(
            Arguments.of(0),
            Arguments.of(2),
            Arguments.of(3),
            Arguments.of(4),
            Arguments.of(5),
            Arguments.of(6),
            Arguments.of(7),
            Arguments.of(8),
            Arguments.of(9),
            Arguments.of(10));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(final int directoryIndex) throws Exception {
        final var diff = DiffBuilder
            .compare(SubtreeFilter.applyRpcSubtreeFilter(getDocument("request.xml", directoryIndex),
                    getDocument("pre-filter.xml", directoryIndex)))
            .withTest(getDocument("post-filter.xml", directoryIndex))
            .ignoreWhitespace()
            .checkForSimilar()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private Document getDocument(final String fileName, final int directoryIndex) throws Exception {
        return XmlUtil.readXmlToDocument(
            SubtreeFilterRpcTest.class.getResourceAsStream("/subtree/rpc/" + directoryIndex + "/" + fileName));
    }
}

/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;

public abstract class EventFormatter<T> implements Immutable {
    private static final XPathFactory XPF = XPathFactory.newInstance();

    // FIXME: NETCONF-369: XPath operates without namespace context, therefore we need an namespace-unaware builder.
    //        Once it is fixed we can use UntrustedXML instead.
    private static final @NonNull DocumentBuilderFactory DBF;

    static {
        final DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setCoalescing(true);
        f.setExpandEntityReferences(false);
        f.setIgnoringElementContentWhitespace(true);
        f.setIgnoringComments(true);
        f.setXIncludeAware(false);
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        DBF = f;
    }

    private final XPathExpression filter;

    EventFormatter()  {
        this.filter = null;
    }

    EventFormatter(final String xpathFilter)
            throws XPathExpressionException {
        final XPath xpath;
        synchronized (XPF) {
            xpath = XPF.newXPath();
        }

        // FIXME: NETCONF-369: we need to bind the namespace context here and for that we need the SchemaContext
        filter = xpath.compile(xpathFilter);
    }

    public final Optional<String> eventData(final SchemaContext schemaContext, final T input, final Instant now)
            throws IOException {
        if (!filterMatches(schemaContext, input, now)) {
            return Optional.empty();
        }

        return Optional.of(createText(schemaContext, input, now));
    }

    abstract void fillDocument(Document doc, SchemaContext schemaContext, T input) throws IOException;

    abstract String createText(SchemaContext schemaContext, T input, Instant now) throws IOException;

    private boolean filterMatches(final SchemaContext schemaContext, final T input, final Instant now)
            throws IOException {
        if (filter == null) {
            return true;
        }

        final Document doc;
        try {
            doc = DBF.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IOException("Failed to create a new document", e);
        }
        fillDocument(doc, schemaContext, input);

        final Boolean eval;
        try {
            eval = (Boolean) filter.evaluate(doc, XPathConstants.BOOLEAN);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Failed to evaluate expression " + filter, e);
        }
        return eval.booleanValue();
    }
}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.opendaylight.netconf.sal.rest.api.Draft02.MediaTypes;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Provider
@Consumes({MediaTypes.PATCH + RestconfService.XML})
public class XmlToPATCHBodyReader extends AbstractIdentifierAwareJaxRsProvider implements
        MessageBodyReader<PATCHContext> {

    private final static Logger LOG = LoggerFactory.getLogger(XmlToPATCHBodyReader.class);
    private static final DocumentBuilderFactory BUILDERFACTORY;

    static {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        BUILDERFACTORY = factory;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public PATCHContext readFrom(Class<PATCHContext> type, Type genericType, Annotation[] annotations, MediaType
            mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException,
            WebApplicationException {

        try {
            final InstanceIdentifierContext<?> path = getInstanceIdentifierContext();

            if (entityStream.available() < 1) {
                // represent empty nopayload input
                return new PATCHContext(path, null, null);
            }

            final DocumentBuilder dBuilder;
            try {
                dBuilder = BUILDERFACTORY.newDocumentBuilder();
            } catch (final ParserConfigurationException e) {
                throw new IllegalStateException("Failed to parse XML document", e);
            }
            final Document doc = dBuilder.parse(entityStream);

            return parse(path, doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);

            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE);
        }
    }

    private PATCHContext parse(final InstanceIdentifierContext<?> pathContext, final Document doc) {
        final List<PATCHEntity> resultCollection = new ArrayList<>();
        final String patchId = doc.getElementsByTagName("patch-id").item(0).getFirstChild().getNodeValue();
        final NodeList editNodes = doc.getElementsByTagName("edit");
        DataSchemaNode schemaNode = (DataSchemaNode) pathContext.getSchemaNode();
        final DomToNormalizedNodeParserFactory parserFactory =
                DomToNormalizedNodeParserFactory.getInstance(XmlUtils.DEFAULT_XML_CODEC_PROVIDER,
                        pathContext.getSchemaContext());

        for (int i = 0; i < editNodes.getLength(); i++) {
            Element element = (Element) editNodes.item(i);
            final String operation = element.getElementsByTagName("operation").item(0).getFirstChild().getNodeValue();
            final String editId = element.getElementsByTagName("edit-id").item(0).getFirstChild().getNodeValue();
            final String target = element.getElementsByTagName("target").item(0).getFirstChild().getNodeValue()
                    .replaceFirst("/", "");

            final NodeList valueNodes = element.getElementsByTagName("value").item(0).getChildNodes();
            Element value = null;
            for (int j = 0; j < valueNodes.getLength(); j++) {
                if (valueNodes.item(j) instanceof Element) {
                    value = (Element) valueNodes.item(j);
                    break;
                }
            }

            String namespace = value.getNamespaceURI();
            org.opendaylight.yangtools.yang.model.api.Module module = pathContext.getSchemaContext()
                    .findModuleByNamespace(URI.create(namespace)).iterator().next();

            String revision = module.getQNameModule().getFormattedRevision();

            Iterator<String> args = Splitter.on("/").split(target.substring(target.indexOf(':') + 1))
                    .iterator();

            DataSchemaNode childNode = (DataSchemaNode) pathContext.getSchemaNode();
            StringBuffer nonCondXpath = new StringBuffer();
            do {
                String s = args.next();
                if (childNode instanceof ContainerSchemaNode) {
                    nonCondXpath.append("/" + s);

                    childNode = ((DataNodeContainer)(childNode))
                            .getDataChildByName(QName.create(namespace, revision, s));
                } else if (childNode instanceof ListSchemaNode) {
                    nonCondXpath.append(
                            "["
                            + ((ListSchemaNode) childNode).getKeyDefinition().iterator().next().getLocalName()
                            + "="
                            + "'" + s + "'"
                            +"]"
                    );

                    if (args.hasNext()) {
                        s = args.next();
                        childNode = ((ListSchemaNode)(childNode))
                                .getDataChildByName(QName.create(namespace, revision, s));
                        nonCondXpath.append("/" + s);
                    }
                }
            } while (args.hasNext());

            if (childNode instanceof ListSchemaNode) {
                String key = ((ListSchemaNode) childNode).getKeyDefinition().iterator().next().getLocalName();

                nonCondXpath.append(
                        "["
                        + key
                        + "="
                        + "'" + ((Element) value).getElementsByTagName(key).item(0).getFirstChild().getNodeValue() + "'"
                        +"]"
                );
            }

            StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(pathContext
                    .getSchemaContext());

            codec.setDefaultPrefix(module.getName());

            final YangInstanceIdentifier targetII = codec.deserialize(codec.serialize(pathContext
                    .getInstanceIdentifier()) + nonCondXpath.toString());

            schemaNode = (DataSchemaNode) SchemaContextUtil.findDataSchemaNode(pathContext.getSchemaContext(),
                    codec.getDataContextTree().getChild(targetII).getDataSchemaNode().getPath());

            SchemaNode targetNode = (DataSchemaNode) SchemaContextUtil.findDataSchemaNode(pathContext.getSchemaContext(),
                    codec.getDataContextTree().getChild(targetII).getDataSchemaNode().getPath().getParent());

            if (targetNode == null) {
                LOG.debug("Target node {} not found in path {} ", target, pathContext.getSchemaNode());
                throw new RestconfDocumentedException("Error parsing input", ErrorType.PROTOCOL,
                        ErrorTag.MALFORMED_MESSAGE);
            } else {
                NormalizedNode<?, ?> parsed = null;
                if (schemaNode instanceof ContainerSchemaNode) {
                    parsed = parserFactory.getContainerNodeParser().parse(Collections.singletonList(value),
                            (ContainerSchemaNode) schemaNode);
                } else if (schemaNode instanceof ListSchemaNode) {
                    NormalizedNode<?, ?> parsedValue = parserFactory.getMapEntryNodeParser().parse(Collections
                            .singletonList(value), (ListSchemaNode) schemaNode);
                    parsed = ImmutableNodes.mapNodeBuilder().withNodeIdentifier(new NodeIdentifier
                            (targetNode.getQName())).withChild((MapEntryNode) parsedValue).build();
                }

                resultCollection.add(new PATCHEntity(editId, operation, targetII.getParent(), parsed));
            }
        }

        return new PATCHContext(pathContext, ImmutableList.copyOf(resultCollection), patchId);
    }
}

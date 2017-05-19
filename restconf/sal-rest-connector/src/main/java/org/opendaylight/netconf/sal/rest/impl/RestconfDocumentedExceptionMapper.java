/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.ForwardingNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines an ExceptionMapper that handles RestconfDocumentedExceptions thrown by resource implementations
 * and translates appropriately to restconf error response as defined in the RESTCONF RFC draft.
 *
 * @author Thomas Pantelis
 */
@Provider
public class RestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    @Context
    private HttpHeaders headers;

    @Override
    public Response toResponse(final RestconfDocumentedException exception) {

        LOG.debug("In toResponse: {}", exception.getMessage());

        final List<MediaType> accepts = headers.getAcceptableMediaTypes();
        accepts.remove(MediaType.WILDCARD_TYPE);

        LOG.debug("Accept headers: {}", accepts);

        final MediaType mediaType;
        if (accepts != null && accepts.size() > 0) {
            mediaType = accepts.get(0); // just pick the first one
        } else {
            // Default to the content type if there's no Accept header
            mediaType = MediaType.APPLICATION_JSON_TYPE;
        }

        LOG.debug("Using MediaType: {}", mediaType);

        final List<RestconfError> errors = exception.getErrors();
        if (errors.isEmpty()) {
            // We don't actually want to send any content but, if we don't set any content here,
            // the tomcat front-end will send back an html error report. To prevent that, set a
            // single space char in the entity.

            return Response.status(exception.getStatus()).type(MediaType.TEXT_PLAIN_TYPE).entity(" ").build();
        }

        final int status = errors.iterator().next().getErrorTag().getStatusCode();

        final ControllerContext context = ControllerContext.getInstance();
        final DataNodeContainer errorsSchemaNode = (DataNodeContainer) context.getRestconfModuleErrorsSchemaNode();

        if (errorsSchemaNode == null) {
            return Response.status(status).type(MediaType.TEXT_PLAIN_TYPE).entity(exception.getMessage()).build();
        }

        Preconditions.checkState(errorsSchemaNode instanceof ContainerSchemaNode,
                "Found Errors SchemaNode isn't ContainerNode");
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> errContBuild =
                Builders.containerBuilder((ContainerSchemaNode) errorsSchemaNode);

        final List<DataSchemaNode> schemaList = ControllerContext.findInstanceDataChildrenByName(errorsSchemaNode,
                Draft02.RestConfModule.ERROR_LIST_SCHEMA_NODE);
        final DataSchemaNode errListSchemaNode = Iterables.getFirst(schemaList, null);
        Preconditions.checkState(
                errListSchemaNode instanceof ListSchemaNode, "Found Error SchemaNode isn't ListSchemaNode");
        final CollectionNodeBuilder<MapEntryNode, MapNode> listErorsBuilder = Builders
                .mapBuilder((ListSchemaNode) errListSchemaNode);


        for (final RestconfError error : errors) {
            listErorsBuilder.withChild(toErrorEntryNode(error, errListSchemaNode));
        }
        errContBuild.withChild(listErorsBuilder.build());

        final NormalizedNodeContext errContext =  new NormalizedNodeContext(new InstanceIdentifierContext<>(null,
                (DataSchemaNode) errorsSchemaNode, null, context.getGlobalSchema()), errContBuild.build());

        Object responseBody;
        if (mediaType.getSubtype().endsWith("json")) {
            responseBody = toJsonResponseBody(errContext, errorsSchemaNode);
        } else {
            responseBody = toXMLResponseBody(errContext, errorsSchemaNode);
        }

        return Response.status(status).type(mediaType).entity(responseBody).build();
    }

    private static MapEntryNode toErrorEntryNode(final RestconfError error, final DataSchemaNode errListSchemaNode) {
        Preconditions.checkArgument(errListSchemaNode instanceof ListSchemaNode,
                "errListSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) errListSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> errNodeValues = Builders
                .mapEntryBuilder(listStreamSchemaNode);

        List<DataSchemaNode> lsChildDataSchemaNode = ControllerContext.findInstanceDataChildrenByName(
                listStreamSchemaNode, "error-type");
        final DataSchemaNode errTypSchemaNode = Iterables.getFirst(lsChildDataSchemaNode, null);
        Preconditions.checkState(errTypSchemaNode instanceof LeafSchemaNode);
        errNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) errTypSchemaNode)
                .withValue(error.getErrorType().getErrorTypeTag()).build());

        lsChildDataSchemaNode = ControllerContext.findInstanceDataChildrenByName(
                listStreamSchemaNode, "error-tag");
        final DataSchemaNode errTagSchemaNode = Iterables.getFirst(lsChildDataSchemaNode, null);
        Preconditions.checkState(errTagSchemaNode instanceof LeafSchemaNode);
        errNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) errTagSchemaNode)
                .withValue(error.getErrorTag().getTagValue()).build());

        if (error.getErrorAppTag() != null) {
            lsChildDataSchemaNode = ControllerContext.findInstanceDataChildrenByName(
                    listStreamSchemaNode, "error-app-tag");
            final DataSchemaNode errAppTagSchemaNode = Iterables.getFirst(lsChildDataSchemaNode, null);
            Preconditions.checkState(errAppTagSchemaNode instanceof LeafSchemaNode);
            errNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) errAppTagSchemaNode)
                    .withValue(error.getErrorAppTag()).build());
        }

        lsChildDataSchemaNode = ControllerContext.findInstanceDataChildrenByName(
                listStreamSchemaNode, "error-message");
        final DataSchemaNode errMsgSchemaNode = Iterables.getFirst(lsChildDataSchemaNode, null);
        Preconditions.checkState(errMsgSchemaNode instanceof LeafSchemaNode);
        errNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) errMsgSchemaNode)
                .withValue(error.getErrorMessage()).build());

        if (error.getErrorInfo() != null) {
            // Oddly, error-info is defined as an empty container in the restconf yang. Apparently the
            // intention is for implementors to define their own data content so we'll just treat it as a leaf
            // with string data.
            errNodeValues.withChild(ImmutableNodes.leafNode(Draft02.RestConfModule.ERROR_INFO_QNAME,
                    error.getErrorInfo()));
        }

        // TODO : find how could we add possible "error-path"

        return errNodeValues.build();
    }

    private static Object toJsonResponseBody(final NormalizedNodeContext errorsNode,
                                             final DataNodeContainer errorsSchemaNode) {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        NormalizedNode<?, ?> data = errorsNode.getData();
        final InstanceIdentifierContext<?> context = errorsNode.getInstanceIdentifierContext();
        final DataSchemaNode schema = (DataSchemaNode) context.getSchemaNode();

        SchemaPath path = context.getSchemaNode().getPath();
        final OutputStreamWriter outputWriter = new OutputStreamWriter(outStream, StandardCharsets.UTF_8);
        if (data == null) {
            throw new RestconfDocumentedException(Response.Status.NOT_FOUND);
        }

        boolean isDataRoot = false;
        URI initialNs = null;
        if (SchemaPath.ROOT.equals(path)) {
            isDataRoot = true;
        } else {
            path = path.getParent();
            // FIXME: Add proper handling of reading root.
        }
        if (!schema.isAugmenting() && !(schema instanceof SchemaContext)) {
            initialNs = schema.getQName().getNamespace();
        }

        final JsonWriter jsonWriter = JsonWriterFactory.createJsonWriter(outputWriter);
        final NormalizedNodeStreamWriter jsonStreamWriter = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                JSONCodecFactory.create(context.getSchemaContext()), path, initialNs, jsonWriter);

        // We create a delegating writer to special-case error-info as error-info is defined as an empty
        // container in the restconf yang schema but we create a leaf node so we can output it. The delegate
        // stream writer validates the node type against the schema and thus will expect a LeafSchemaNode but
        // the schema has a ContainerSchemaNode so, to avoid an error, we override the leafNode behavior
        // for error-info.
        final NormalizedNodeStreamWriter streamWriter = new ForwardingNormalizedNodeStreamWriter() {
            @Override
            protected NormalizedNodeStreamWriter delegate() {
                return jsonStreamWriter;
            }

            @Override
            public void leafNode(final NodeIdentifier name, final Object value) throws IOException {
                if (name.getNodeType().equals(Draft02.RestConfModule.ERROR_INFO_QNAME)) {
                    jsonWriter.name(Draft02.RestConfModule.ERROR_INFO_QNAME.getLocalName());
                    jsonWriter.value(value.toString());
                } else {
                    super.leafNode(name, value);
                }
            }
        };

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter);
        try {
            if (isDataRoot) {
                writeDataRoot(outputWriter,nnWriter,(ContainerNode) data);
            } else {
                if (data instanceof MapEntryNode) {
                    data = ImmutableNodes.mapNodeBuilder(data.getNodeType()).withChild((MapEntryNode) data).build();
                }
                nnWriter.write(data);
            }
            nnWriter.flush();
            outputWriter.flush();
        } catch (final IOException e) {
            LOG.warn("Error writing error response body", e);
        }

        return outStream.toString();

    }

    private static Object toXMLResponseBody(final NormalizedNodeContext errorsNode,
                                            final DataNodeContainer errorsSchemaNode) {
        final InstanceIdentifierContext<?> pathContext = errorsNode.getInstanceIdentifierContext();
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        final XMLStreamWriter xmlWriter;
        try {
            xmlWriter = XML_FACTORY.createXMLStreamWriter(outStream, StandardCharsets.UTF_8.name());
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (final FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        NormalizedNode<?, ?> data = errorsNode.getData();
        SchemaPath schemaPath = pathContext.getSchemaNode().getPath();

        boolean isDataRoot = false;
        if (SchemaPath.ROOT.equals(schemaPath)) {
            isDataRoot = true;
        } else {
            schemaPath = schemaPath.getParent();
        }

        final NormalizedNodeStreamWriter xmlStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                pathContext.getSchemaContext(), schemaPath);

        // We create a delegating writer to special-case error-info as error-info is defined as an empty
        // container in the restconf yang schema but we create a leaf node so we can output it. The delegate
        // stream writer validates the node type against the schema and thus will expect a LeafSchemaNode but
        // the schema has a ContainerSchemaNode so, to avoid an error, we override the leafNode behavior
        // for error-info.
        final NormalizedNodeStreamWriter streamWriter = new ForwardingNormalizedNodeStreamWriter() {
            @Override
            protected NormalizedNodeStreamWriter delegate() {
                return xmlStreamWriter;
            }

            @Override
            public void leafNode(final NodeIdentifier name, final Object value) throws IOException {
                if (name.getNodeType().equals(Draft02.RestConfModule.ERROR_INFO_QNAME)) {
                    String ns = Draft02.RestConfModule.ERROR_INFO_QNAME.getNamespace().toString();
                    try {
                        xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX,
                                Draft02.RestConfModule.ERROR_INFO_QNAME.getLocalName(), ns);
                        xmlWriter.writeCharacters(value.toString());
                        xmlWriter.writeEndElement();
                    } catch (XMLStreamException e) {
                        throw new IOException("Error writing error-info", e);
                    }
                } else {
                    super.leafNode(name, value);
                }
            }
        };

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter);
        try {
            if (isDataRoot) {
                writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
            } else {
                if (data instanceof MapEntryNode) {
                    // Restconf allows returning one list item. We need to wrap it
                    // in map node in order to serialize it properly
                    data = ImmutableNodes.mapNodeBuilder(data.getNodeType()).addChild((MapEntryNode) data).build();
                }
                nnWriter.write(data);
                nnWriter.flush();
            }
        } catch (final IOException e) {
            LOG.warn("Error writing error response body.", e);
        }

        return outStream.toString();
    }

    private static void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter,
                                         final ContainerNode data)
            throws IOException {
        try {
            final QName name = SchemaContext.NAME;
            xmlWriter.writeStartElement(name.getNamespace().toString(), name.getLocalName());
            for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
                nnWriter.write(child);
            }
            nnWriter.flush();
            xmlWriter.writeEndElement();
            xmlWriter.flush();
        } catch (final XMLStreamException e) {
            Throwables.propagate(e);
        }
    }

    private static void writeDataRoot(final OutputStreamWriter outputWriter, final NormalizedNodeWriter nnWriter,
                                      final ContainerNode data) throws IOException {
        final Iterator<DataContainerChild<? extends PathArgument, ?>> iterator = data.getValue().iterator();
        while (iterator.hasNext()) {
            final DataContainerChild<? extends PathArgument, ?> child = iterator.next();
            nnWriter.write(child);
            nnWriter.flush();
        }
    }
}

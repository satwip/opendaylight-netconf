/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableUnkeyedListEntryNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableUnkeyedListNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Mapper that is responsible for transformation of thrown {@link RestconfDocumentedException} to errors structure
 *  that is modelled by RESTCONF module (see section 8 of RFC-8040).
 */
@Provider
public final class RestconfDocumentedExceptionMapper implements ExceptionMapper<RestconfDocumentedException> {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDocumentedExceptionMapper.class);
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE;
    private static final MediaType YANG_DATA_JSON_TYPE = MediaType.valueOf("application/yang-data+json");
    private static final MediaType YANG_DATA_XML_TYPE = MediaType.valueOf("application/yang-data+xml");
    private static final List<QName> ERRORS_CONTAINER_PATH = Lists.newArrayList(
            RestconfModule.ERRORS_GROUPING_QNAME, RestconfModule.ERRORS_CONTAINER_QNAME);

    @Context
    private HttpHeaders headers;
    private final BindingNormalizedNodeSerializer codec;
    private final SchemaContextHandler schemaContextHandler;

    /**
     * Initialization of the exception mapper.
     *
     * @param codec                Codec used for transformation of {@link YangInstanceIdentifier}
     *                             to {@link InstanceIdentifier} (error-path leaf).
     * @param schemaContextHandler Handler that provides actual schema context.
     */
    public RestconfDocumentedExceptionMapper(final BindingNormalizedNodeSerializer codec,
                                             final SchemaContextHandler schemaContextHandler) {
        this.codec = codec;
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    @SuppressFBWarnings(value = "SLF4J_MANUALLY_PROVIDED_MESSAGE", justification = "In the debug messages "
            + "we don't to have full stack trace - getMessage(..) method provides finer output.")
    public Response toResponse(final RestconfDocumentedException exception) {
        LOG.debug("Starting to map received exception to error response: {}", exception.getMessage());
        final Response preparedResponse;
        if (exception.getErrors().isEmpty()) {
            preparedResponse = processExceptionWithoutErrors(exception);
        } else {
            preparedResponse = processExceptionWithErrors(exception);
        }
        LOG.debug("Exception {} has been successfully mapped to response: {}",
                exception.getMessage(), preparedResponse);
        return preparedResponse;
    }

    /**
     * Building of response from exception that doesn't contain any errors in the embedded list.
     *
     * @param exception Exception thrown during processing of HTTP request.
     * @return Built HTTP response.
     */
    private static Response processExceptionWithoutErrors(final RestconfDocumentedException exception) {
        if (!exception.getStatus().equals(Response.Status.FORBIDDEN)
                && exception.getStatus().getFamily().equals(Response.Status.Family.CLIENT_ERROR)) {
            // there should be some error messages, creation of WARN log
            LOG.warn("Input exception has a family of 4xx but doesn't contain any descriptive errors: {}",
                    exception.getMessage());
        }
        // We don't actually want to send any content but, if we don't set any content here, the tomcat front-end
        // will send back an html error report. To prevent that, set a single space char in the entity.
        return Response.status(exception.getStatus())
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(" ")
                .build();
    }

    /**
     * Building of response from exception that contains non-empty list of errors.
     *
     * @param exception Exception thrown during processing of HTTP request.
     * @return Built HTTP response.
     */
    private Response processExceptionWithErrors(final RestconfDocumentedException exception) {
        final ContainerNode errorsContainer = buildErrorsContainer(exception);
        final String serializedResponseBody;
        final MediaType responseMediaType = buildResponseMediaType(buildResponseMediaType());
        if (responseMediaType.equals(YANG_DATA_JSON_TYPE)) {
            serializedResponseBody = serializeErrorsContainerToJson(errorsContainer);
        } else {
            serializedResponseBody = serializeErrorsContainerToXml(errorsContainer);
        }
        return Response.status(exception.getStatus())
                .type(responseMediaType)
                .entity(serializedResponseBody)
                .build();
    }

    /**
     * Filling up of the errors container with data from input {@link RestconfDocumentedException}.
     *
     * @param exception Thrown exception.
     * @return Built errors container.
     */
    private ContainerNode buildErrorsContainer(final RestconfDocumentedException exception) {
        final List<UnkeyedListEntryNode> errorEntries = exception.getErrors().stream()
                .map(this::createErrorEntry)
                .collect(Collectors.toList());
        return ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                        RestconfModule.RESTCONF_CONTAINER_QNAME))
                .withChild(ImmutableUnkeyedListNodeBuilder.create()
                        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(
                                RestconfModule.ERROR_LIST_QNAME))
                        .withValue(errorEntries)
                        .build())
                .build();
    }

    /**
     * Building of one error entry using provided {@link RestconfError}.
     *
     * @param restconfError Error details.
     * @return Built list entry.
     */
    private UnkeyedListEntryNode createErrorEntry(final RestconfError restconfError) {
        // filling in mandatory leafs
        final DataContainerNodeBuilder<NodeIdentifier, UnkeyedListEntryNode> entryBuilder
                = ImmutableUnkeyedListEntryNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(RestconfModule.ERROR_LIST_QNAME))
                .withChild(ImmutableNodes.leafNode(RestconfModule.ERROR_TYPE_QNAME, restconfError.getErrorType()))
                .withChild(ImmutableNodes.leafNode(RestconfModule.ERROR_TAG_QNAME, restconfError.getErrorTag()));

        // filling in optional fields
        if (restconfError.getErrorMessage() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_MESSAGE_QNAME, restconfError.getErrorMessage()));
        }
        if (restconfError.getErrorAppTag() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_APP_TAG_QNAME, restconfError.getErrorAppTag()));
        }
        if (restconfError.getErrorInfo() != null) {
            // Oddly, error-info is defined as an empty container in the restconf yang. Apparently the
            // intention is for implementors to define their own data content so we'll just treat it as a leaf
            // with string data.
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_INFO_QNAME, restconfError.getErrorInfo()));
        }

        if (restconfError.getErrorPath() != null) {
            entryBuilder.withChild(ImmutableNodes.leafNode(
                    RestconfModule.ERROR_PATH_QNAME, codec.fromYangInstanceIdentifier(restconfError.getErrorPath())));
        }
        return entryBuilder.build();
    }

    /**
     * Serialization of the errors container into JSON representation.
     *
     * @param errorsContainer To be serialized errors container.
     * @return JSON representation of the errors container.
     */
    private String serializeErrorsContainerToJson(final ContainerNode errorsContainer) {
        final ContainerSchemaNode errorsSchemaNode = (ContainerSchemaNode) SchemaContextUtil
                .findNodeInSchemaContext(schemaContextHandler.get(), ERRORS_CONTAINER_PATH);
        final URI initialNamespace = errorsSchemaNode.getQName().getNamespace();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             OutputStreamWriter streamStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             StreamWriterWithDisabledValidation jsonStreamWriter = new JsonStreamWriterWithDisabledValidation(
                     RestconfModule.ERROR_INFO_QNAME, streamStreamWriter, errorsSchemaNode.getPath(),
                     initialNamespace, schemaContextHandler);
             NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(jsonStreamWriter)
        ) {
            return writeNormalizedNode(errorsContainer, outputStream, nnWriter);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close some of the output JSON writers", e);
        }
    }

    /**
     * Serialization of the errors container into XML representation.
     *
     * @param errorsContainer To be serialized errors container.
     * @return XML representation of the errors container.
     */
    private String serializeErrorsContainerToXml(final ContainerNode errorsContainer) {
        final ContainerSchemaNode errorsSchemaNode = (ContainerSchemaNode) SchemaContextUtil
                .findNodeInSchemaContext(schemaContextHandler.get(), ERRORS_CONTAINER_PATH);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             StreamWriterWithDisabledValidation streamWriter = new XmlStreamWriterWithDisabledValidation(
                     RestconfModule.ERROR_INFO_QNAME, outputStream, errorsSchemaNode.getPath(), schemaContextHandler);
             NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter)
        ) {
            return writeNormalizedNode(errorsContainer, outputStream, nnWriter);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close some of the output XML writers", e);
        }
    }

    private static String writeNormalizedNode(final NormalizedNode<?, ?> errorsContainer,
            final ByteArrayOutputStream outputStream, final NormalizedNodeWriter nnWriter) {
        try {
            nnWriter.write(errorsContainer);
            nnWriter.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write error response body", e);
        }
        try {
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Output stream cannot be converted to string representation", e);
        }
    }

    /**
     * Selection of media type that will be used for suffix of 'application/yang-data'. Selection criteria are described
     * in RFC 8040, section 7.1. At the first step, accepted media-type is analyzed and only supported media-types
     * are filtered. If both XML and JSON media-types are accepted, JSON is selected as a default one used in RESTCONF.
     * If accepted-media type is not specified, the media-type used in request is chosen only if it is supported one.
     * If it is not supported or it is not specified at all, again, the default one (JSON) is selected.
     *
     * @return Media type.
     */
    private MediaType buildResponseMediaType() {
        final List<MediaType> acceptableMediaTypes = headers.getAcceptableMediaTypes();
        final List<MediaType> acceptableAndSupportedMediaTypes = acceptableMediaTypes.stream()
                .filter(RestconfDocumentedExceptionMapper::isCompatibleMediaType)
                .collect(Collectors.toList());
        if (acceptableAndSupportedMediaTypes.size() == 0) {
            // check content type of the request
            final MediaType requestMediaType = headers.getMediaType();
            if (isCompatibleMediaType(requestMediaType)) {
                return requestMediaType;
            } else {
                LOG.warn("Request doesn't specify accepted media-types and the media-type '{}' used by request is "
                        + "not supported - using of default '{}' media-type.", requestMediaType, DEFAULT_MEDIA_TYPE);
                return DEFAULT_MEDIA_TYPE;
            }
        } else if (acceptableAndSupportedMediaTypes.size() == 1
                && acceptableAndSupportedMediaTypes.get(0).equals(MediaType.WILDCARD_TYPE)) {
            // choose server-preferred type
            return DEFAULT_MEDIA_TYPE;
        } else if (acceptableAndSupportedMediaTypes.size() == 1) {
            // choose the only-one accepted media type
            return acceptableAndSupportedMediaTypes.get(0);
        } else {
            // choose the server-preferred type
            return DEFAULT_MEDIA_TYPE;
        }
    }

    /**
     * Mapping of JSON-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_JSON_TYPE}
     * or XML-compatible type to {@link RestconfDocumentedExceptionMapper#YANG_DATA_XML_TYPE}.
     *
     * @param mediaTypeBase Base media type from which the response media-type is built.
     * @return Derived media type.
     */
    private static MediaType buildResponseMediaType(final MediaType mediaTypeBase) {
        if (mediaTypeBase.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return YANG_DATA_JSON_TYPE;
        } else {
            return YANG_DATA_XML_TYPE;
        }
    }

    private static boolean isCompatibleMediaType(final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE);
    }
}
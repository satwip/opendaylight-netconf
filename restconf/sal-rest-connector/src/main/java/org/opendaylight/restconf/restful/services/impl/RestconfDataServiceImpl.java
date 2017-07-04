/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.CREATE_NOTIFICATION_STREAM;
import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.STREAM_ACCESS_PATH_PART;
import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.STREAM_LOCATION_PATH_PART;
import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.STREAM_PATH;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.netconf.sal.restconf.impl.PatchStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.restconf.impl.WriterParameters;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfDataService;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.restful.utils.*;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfDataService}.
 */
public class RestconfDataServiceImpl implements RestconfDataService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataServiceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final SchemaContextHandler schemaContextHandler;
    private final TransactionChainHandler transactionChainHandler;
    private final DOMMountPointServiceHandler mountPointServiceHandler;

    private final RestconfStreamsSubscriptionService delegRestconfSubscrService;

    public RestconfDataServiceImpl(final SchemaContextHandler schemaContextHandler,
                                   final TransactionChainHandler transactionChainHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler,
            final RestconfStreamsSubscriptionService delegRestconfSubscrService) {
        this.schemaContextHandler = schemaContextHandler;
        this.transactionChainHandler = transactionChainHandler;
        this.mountPointServiceHandler = mountPointServiceHandler;
        this.delegRestconfSubscrService = delegRestconfSubscrService;
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return readData(null, uriInfo);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef.get(), Optional.of(this.mountPointServiceHandler.get()));

        boolean withDefaUsed = false;
        String withDefa = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "with-defaults":
                    if (!withDefaUsed) {
                        withDefaUsed = true;
                        withDefa = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("With-defaults parameter can be used only once.");
                    }
                    break;
                default:
                    LOG.info("Unknown key : {}.", entry.getKey());
                    break;
            }
        }
        boolean tagged = false;
        if (withDefaUsed) {
            if ("report-all-tagged".equals(withDefa)) {
                tagged = true;
                withDefa = null;
            }
            if ("report-all".equals(withDefa)) {
                withDefa = null;
            }
        }

        final WriterParameters parameters = ReadDataTransactionUtil.parseUriParameters(
                instanceIdentifier, uriInfo, tagged);

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final DOMTransactionChain transactionChain;
        if (mountPoint == null) {
            transactionChain = this.transactionChainHandler.get();
        } else {
            transactionChain = transactionChainOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                instanceIdentifier, mountPoint, transactionChain);
        final NormalizedNode<?, ?> node =
                ReadDataTransactionUtil.readData(identifier, parameters.getContent(), transactionNode, withDefa,
                        schemaContextRef, uriInfo);
        if (identifier.contains(STREAM_PATH) && identifier.contains(STREAM_ACCESS_PATH_PART)
                && identifier.contains(STREAM_LOCATION_PATH_PART)) {
            final String value = (String) node.getValue();
            final String streamName = value.substring(
                    value.indexOf(CREATE_NOTIFICATION_STREAM.toString() + RestconfConstants.SLASH),
                    value.length());
            this.delegRestconfSubscrService.subscribeToStream(streamName, uriInfo);
        }
        if (node == null) {
            throw new RestconfDocumentedException(
                    "Request could not be completed because the relevant data model content does not exist",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.DATA_MISSING);
        }

        if ((parameters.getContent().equals(RestconfDataServiceConstant.ReadData.ALL))
                    || parameters.getContent().equals(RestconfDataServiceConstant.ReadData.CONFIG)) {
            return Response.status(200)
                    .entity(new NormalizedNodeContext(instanceIdentifier, node, parameters))
                    .header("ETag", '"' + node.getNodeType().getModule().getFormattedRevision()
                        + node.getNodeType().getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
        }

        return Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node, parameters)).build();
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        Preconditions.checkNotNull(payload);

        final String insert = "insert";
        final String point = "point";
        Set<String> uriEntriesExpected = new HashSet<>();
        uriEntriesExpected.add(insert);
        uriEntriesExpected.add(point);
        URIEntrySetUtils processURIEntries = new URIEntrySetUtils(uriEntriesExpected, uriInfo);

        checkQueryParams(processURIEntries.getEntriesSet().get(insert).isKeyUsed(),
                processURIEntries.getEntriesSet().get(point).isKeyUsed(),
                processURIEntries.getEntriesSet().get(insert).getKeyValue());

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final DOMTransactionChain transactionChain;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transactionChain = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transactionChain = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, transactionChain);
        return PutDataTransactionUtil.putData(payload, ref, transactionNode,
                processURIEntries.getEntriesSet().get(insert).getKeyValue(),
                processURIEntries.getEntriesSet().get(point).getKeyValue());
    }

    private static void checkQueryParams(final boolean insertUsed, final boolean pointUsed, final String insert) {
        if (pointUsed && !insertUsed) {
            throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.");
        }
        if (pointUsed && (insert.equals("first") || insert.equals("last"))) {
            throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        Preconditions.checkNotNull(payload);
        final URIEntrySetUtils.InsertAndPoint uriEntrySetPoint = new URIEntrySetUtils.InsertAndPoint(uriInfo);

        checkQueryParams(uriEntrySetPoint.insertUsed, uriEntrySetPoint.pointUsed, uriEntrySetPoint.insert);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final DOMTransactionChain transactionChain;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transactionChain = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transactionChain = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }
        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, transactionChain);
        return PostDataTransactionUtil.postData(uriInfo, payload, transactionNode, ref, uriEntrySetPoint.insert, uriEntrySetPoint.point);
    }

    @Override
    public Response deleteData(final String identifier) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef.get(), Optional.of(this.mountPointServiceHandler.get()));

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final DOMTransactionChain transactionChain;
        if (mountPoint == null) {
            transactionChain = this.transactionChainHandler.get();
        } else {
            transactionChain = transactionChainOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                transactionChain);
        return DeleteDataTransactionUtil.deleteData(transactionNode);
    }

    @Override
    public PatchStatusContext patchData(final String identifier, final PatchContext context, final UriInfo uriInfo) {
        return patchData(context, uriInfo);
    }

    @Override
    public PatchStatusContext patchData(final PatchContext context, final UriInfo uriInfo) {
        Preconditions.checkNotNull(context);
        final DOMMountPoint mountPoint = context.getInstanceIdentifierContext().getMountPoint();

        final DOMTransactionChain transactionChain;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            transactionChain = this.transactionChainHandler.get();
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            transactionChain = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                context.getInstanceIdentifierContext(), mountPoint, transactionChain);

        return PatchDataTransactionUtil.patchData(context, transactionNode, ref);
    }

    /**
     * Prepare transaction chain to access data of mount point.
     * @param mountPoint
     *            mount point reference
     * @return {@link DOMTransactionChain}
     */
    private static DOMTransactionChain transactionChainOfMountPoint(@Nonnull final DOMMountPoint mountPoint) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return domDataBrokerService.get().createTransactionChain(RestConnectorProvider.TRANSACTION_CHAIN_LISTENER);
        }

        final String errMsg = "DOM data broker service isn't available for mount point " + mountPoint.getIdentifier();
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.net.URI;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.yang.gen.v1.subscribe.to.notification.rev161028.Notifi;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}.
 */
@Path("/")
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);
    private static final QName LOCATION_QNAME = QName.create(Notifi.QNAME, "location").intern();
    private static final NodeIdentifier LOCATION_NODEID = NodeIdentifier.create(LOCATION_QNAME);

    private final SubscribeToStreamUtil streamUtils;
    private final HandlersHolder handlersHolder;
    private final ListenersBroker listenersBroker;

    /**
     * Initialize holder of handlers with holders as parameters.
     *
     * @param dataBroker {@link DOMDataBroker}
     * @param notificationService {@link DOMNotificationService}
     * @param databindProvider a {@link DatabindProvider}
     * @param configuration configuration for RESTCONF {@link StreamsConfiguration}}
     */
    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBroker dataBroker,
            final DOMNotificationService notificationService, final DatabindProvider databindProvider,
            final ListenersBroker listenersBroker, final StreamsConfiguration configuration) {
        handlersHolder = new HandlersHolder(dataBroker, notificationService, databindProvider);
        streamUtils = configuration.useSSE() ? SubscribeToStreamUtil.serverSentEvents(listenersBroker)
                : SubscribeToStreamUtil.webSockets(listenersBroker);
        this.listenersBroker = listenersBroker;
    }

    @Override
    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final var params = QueryParams.newNotificationQueryParams(uriInfo);

        final URI location;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            location = streamUtils.subscribeToDataStream(identifier, uriInfo, params, handlersHolder);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            location = streamUtils.subscribeToYangStream(identifier, uriInfo, params, handlersHolder);
        } else {
            final String msg = "Bad type of notification of sal-remote";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg);
        }

        return Response.ok()
            .location(location)
            .entity(new NormalizedNodePayload(
                Inference.ofDataTreePath(handlersHolder.getDatabindProvider().currentContext().modelContext(),
                    Notifi.QNAME, LOCATION_QNAME),
                ImmutableNodes.leafNode(LOCATION_NODEID, location.toString())))
            .build();
    }

    /**
     * Holder of all handlers for notifications.
     */
    // FIXME: why do we even need this class?!
    public static final class HandlersHolder {
        private final DOMDataBroker dataBroker;
        private final DOMNotificationService notificationService;
        private final DatabindProvider databindProvider;

        private HandlersHolder(final DOMDataBroker dataBroker, final DOMNotificationService notificationService,
                final DatabindProvider databindProvider) {
            this.dataBroker = dataBroker;
            this.notificationService = notificationService;
            this.databindProvider = databindProvider;
        }

        /**
         * Get {@link DOMDataBroker}.
         *
         * @return the dataBroker
         */
        public DOMDataBroker getDataBroker() {
            return dataBroker;
        }

        /**
         * Get {@link DOMNotificationService}.
         *
         * @return the notificationService
         */
        public DOMNotificationService getNotificationServiceHandler() {
            return notificationService;
        }

        /**
         * Get {@link DatabindProvider}.
         *
         * @return the schemaHandler
         */
        public DatabindProvider getDatabindProvider() {
            return databindProvider;
        }
    }
}

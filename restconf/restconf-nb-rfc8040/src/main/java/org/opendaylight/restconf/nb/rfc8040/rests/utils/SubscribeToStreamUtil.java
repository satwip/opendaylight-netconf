/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe to stream util class.
 */
public final class SubscribeToStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToStreamUtil.class);
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HH:MM", "Z").toFormatter();

    private SubscribeToStreamUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Register listener by streamName in identifier to listen to yang notifications, and put or delete information
     * about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Name of the stream.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @return Stream location for listening.
     */
    @SuppressWarnings("rawtypes")
    public static URI subscribeToYangStream(final String identifier,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final Optional<NotificationListenerAdapter> notificationListenerAdapter =
                ListenersBroker.getInstance().getNotificationListenerFor(streamName);

        if (!notificationListenerAdapter.isPresent()) {
            throw new RestconfDocumentedException(String.format(
                    "Stream with name %s was not found.", streamName),
                    ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }

        registerToListenNotification(
                notificationListenerAdapter.get(), handlersHolder.getNotificationServiceHandler());
        notificationListenerAdapter.get().setQueryParams(
                notificationQueryParams.getStart(),
                notificationQueryParams.getStop().orElse(null),
                notificationQueryParams.getFilter().orElse(null),
                false);
        notificationListenerAdapter.get().setCloseVars(
                handlersHolder.getTransactionChainHandler(), handlersHolder.getSchemaHandler());

        final SchemaContext schemaContext = handlersHolder.getSchemaHandler().get();
        final String serializedPath = SchemaPathCodec.serialize(notificationListenerAdapter.get().getSchemaPath(),
                schemaContext);
        final MapEntryNode streamNode = RestconfMappingNodeUtil.mapStreamSubscription(serializedPath,
                notificationQueryParams.getStart());
        final DOMDataTreeWriteTransaction writeTransaction = handlersHolder
                .getTransactionChainHandler().get()
                .newWriteOnlyTransaction();
        writeDataToDS(schemaContext, serializedPath, writeTransaction, streamNode);
        submitData(writeTransaction);

        return prepareUriByStreamName(streamName);
    }

    /**
     * Prepare InstanceIdentifierContext for Location leaf.
     *
     * @param schemaHandler Schema context handler.
     * @return InstanceIdentifier of Location leaf.
     */
    public static InstanceIdentifierContext<?> prepareIIDSubsStreamOutput(final SchemaContextHandler schemaHandler) {
        final Optional<Module> module = schemaHandler.get()
                .findModule(RestconfStreamsConstants.NOTIFI_QNAME.getModule());
        Preconditions.checkState(module.isPresent());
        final Optional<DataSchemaNode> notify = module.get()
                .findDataChildByName(RestconfStreamsConstants.NOTIFI_QNAME);
        Preconditions.checkState(notify.isPresent());
        final Optional<DataSchemaNode> location = ((ContainerSchemaNode) notify.get())
                .findDataChildByName(RestconfStreamsConstants.LOCATION_QNAME);
        Preconditions.checkState(location.isPresent());

        final List<PathArgument> path = new ArrayList<>();
        path.add(NodeIdentifier.create(RestconfStreamsConstants.NOTIFI_QNAME));
        path.add(NodeIdentifier.create(RestconfStreamsConstants.LOCATION_QNAME));
        return new InstanceIdentifierContext<SchemaNode>(YangInstanceIdentifier.create(path), location.get(),
                null, schemaHandler.get());
    }

    /**
     * Register listener by streamName in identifier to listen to data change notifications, and put or delete
     * information about listener to DS according to ietf-restconf-monitoring.
     *
     * @param identifier              Identifier as stream name.
     * @param notificationQueryParams Query parameters of notification.
     * @param handlersHolder          Holder of handlers for notifications.
     * @return Location for listening.
     */
    @SuppressWarnings("rawtypes")
    public static URI subscribeToDataStream(final String identifier,
            final NotificationQueryParams notificationQueryParams, final HandlersHolder handlersHolder) {
        final Map<String, String> mapOfValues = mapValuesFromUri(identifier);
        final LogicalDatastoreType datastoreType = parseURIEnum(
                LogicalDatastoreType.class,
                mapOfValues.get(RestconfStreamsConstants.DATASTORE_PARAM_NAME));
        if (datastoreType == null) {
            final String message = "Stream name doesn't contain datastore value (pattern /datastore=)";
            LOG.debug(message);
            throw new RestconfDocumentedException(message, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final DataChangeScope scope = parseURIEnum(
                DataChangeScope.class,
                mapOfValues.get(RestconfStreamsConstants.SCOPE_PARAM_NAME));
        if (scope == null) {
            final String message = "Stream name doesn't contains datastore value (pattern /scope=)";
            LOG.warn(message);
            throw new RestconfDocumentedException(message, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final String streamName = ListenersBroker.createStreamNameFromUri(identifier);
        final Optional<ListenerAdapter> listener = ListenersBroker.getInstance().getDataChangeListenerFor(streamName);
        Preconditions.checkArgument(listener.isPresent(), "Listener doesn't exist : " + streamName);

        listener.get().setQueryParams(
                notificationQueryParams.getStart(),
                notificationQueryParams.getStop().orElse(null),
                notificationQueryParams.getFilter().orElse(null),
                false);
        listener.get().setCloseVars(handlersHolder.getTransactionChainHandler(), handlersHolder.getSchemaHandler());
        registration(datastoreType, listener.get(), handlersHolder.getDomDataBrokerHandler().get());

        final SchemaContext schemaContext = handlersHolder.getSchemaHandler().get();
        final String serializedPath = IdentifierCodec.serialize(listener.get().getPath(), schemaContext);
        final MapEntryNode streamNode = RestconfMappingNodeUtil.mapStreamSubscription(
                serializedPath, notificationQueryParams.getStart());
        final DOMDataTreeWriteTransaction writeTransaction = handlersHolder.getTransactionChainHandler().get()
                .newWriteOnlyTransaction();
        writeDataToDS(schemaContext, serializedPath, writeTransaction, streamNode);
        submitData(writeTransaction);
        return prepareUriByStreamName(streamName);
    }

    /**
     * Parse input of query parameters - start-time or stop-time - from {@link DateAndTime} format
     * to {@link Instant} format.
     *
     * @param entry Start-time or stop-time as string in {@link DateAndTime} format.
     * @return Parsed {@link Instant} by entry.
     */
    public static Instant parseDateFromQueryParam(final Entry<String, List<String>> entry) {
        final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
        final String value = event.getValue();
        final TemporalAccessor accessor;
        try {
            accessor = FORMATTER.parse(value);
        } catch (final DateTimeParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + value, e);
        }
        return Instant.from(accessor);
    }

    private static void writeDataToDS(final SchemaContext schemaContext, final String streamName,
            final DOMDataTreeWriteTransaction writeTransaction, final MapEntryNode streamNode) {
        try {
            final String pathId = MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY
                    + URLEncoder.encode(streamName, StandardCharsets.UTF_8.name());
            writeTransaction.merge(LogicalDatastoreType.OPERATIONAL,
                    IdentifierCodec.deserialize(pathId, schemaContext), streamNode);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void submitData(final DOMDataTreeWriteTransaction writeTransaction) {
        try {
            writeTransaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while putting data to DS.", e);
        }
    }

    /**
     * Prepare map of URI parameter-values.
     *
     * @param identifier String identification of URI.
     * @return Map od URI parameters and values.
     */
    private static Map<String, String> mapValuesFromUri(final String identifier) {
        final HashMap<String, String> result = new HashMap<>();
        for (final String token : RestconfConstants.SLASH_SPLITTER.split(identifier)) {
            final String[] paramToken = token.split(String.valueOf(RestconfStreamsConstants.EQUAL));
            if (paramToken.length == 2) {
                result.put(paramToken[0], paramToken[1]);
            }
        }
        return result;
    }

    /**
     * Building of URI from stream name by appending of base part of URI.
     *
     * @param streamName Name of the stream without leading of trailing slash.
     * @return Created URI.
     */
    static URI prepareUriByStreamName(final String streamName) {
        return URI.create(RestconfConstants.BASE_URI_PATTERN + RestconfConstants.SLASH + streamName);
    }

    /**
     * Register data change listener in DOM data broker and set it to listener on stream.
     *
     * @param datastore     {@link LogicalDatastoreType}
     * @param listener      listener on specific stream
     * @param domDataBroker data broker for register data change listener
     */
    private static void registration(final LogicalDatastoreType datastore, final ListenerAdapter listener,
            final DOMDataBroker domDataBroker) {
        if (listener.isListening()) {
            return;
        }

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        if (changeService == null) {
            throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService");
        }

        final DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(datastore, listener.getPath());
        final ListenerRegistration<ListenerAdapter> registration =
                changeService.registerDataTreeChangeListener(root, listener);
        listener.setRegistration(registration);
    }

    private static void registerToListenNotification(final NotificationListenerAdapter listener,
            final NotificationServiceHandler notificationServiceHandler) {
        if (listener.isListening()) {
            return;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration =
                notificationServiceHandler.get().registerNotificationListener(listener, path);
        listener.setRegistration(registration);
    }

    /**
     * Parse out enumeration from URI.
     *
     * @param clazz Target enumeration type.
     * @param value String representation of enumeration value.
     * @return Parsed enumeration type.
     */
    private static <T> T parseURIEnum(final Class<T> clazz, final String value) {
        if (value == null || value.equals("")) {
            return null;
        }
        return ResolveEnumUtil.resolveEnum(clazz, value);
    }
}
/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.VerifyException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ReceiveEventsParams;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Features of subscribing part of both notifications.
 */
abstract class AbstractCommonSubscriber<T> extends AbstractNotificationsData implements BaseListenerInterface {
    private record Subscriber<T>(EventFormatter<T> formatter, StreamSessionHandler handler) {
        Subscriber {
            requireNonNull(formatter);
            requireNonNull(handler);
        }
    }

    private static abstract sealed class Subscribers<T> {
        static final class Single<T> extends Subscribers<T> {
            private final Subscriber<T> subscriber;

            Single(final Subscriber<T> subscriber) {
                this.subscriber = requireNonNull(subscriber);
            }

            @Override
            Subscribers<T> add(final Subscriber<T> toAdd) {
                return new Multiple<>(ImmutableListMultimap.of(
                    subscriber.formatter, subscriber,
                    toAdd.formatter, toAdd));
            }

            @Override
            Subscribers<T> remove(final Subscriber<T> toRemove) {
                return toRemove.equals(subscriber) ? null : this;
            }

            @Override
            void formatAndPublish(final EffectiveModelContext modelContext, final T input, final Instant now) {
                final var formatted = format(subscriber.formatter, modelContext, input, now);
                if (formatted != null) {
                    subscriber.handler.sendDataMessage(formatted);
                }
            }
        }

        static final class Multiple<T> extends Subscribers<T> {
            private final ImmutableListMultimap<EventFormatter<T>, Subscriber<T>> subscribers;

            Multiple(final ListMultimap<EventFormatter<T>, Subscriber<T>> subscribers) {
                this.subscribers = ImmutableListMultimap.copyOf(subscribers);
            }

            @Override
            Subscribers<T> add(final Subscriber<T> toAdd) {
                final var newSubscribers = ArrayListMultimap.create(subscribers);
                newSubscribers.put(toAdd.formatter, toAdd);
                return new Multiple<>(newSubscribers);
            }

            @Override
            Subscribers<T> remove(final Subscriber<T> toRemove) {
                final var newSubscribers = ArrayListMultimap.create(subscribers);
                return newSubscribers.remove(toRemove.formatter, toRemove)
                    ? switch (newSubscribers.size()) {
                        case 0 -> throw new VerifyException("Unexpected empty subscribers");
                        case 1 -> new Single<>(newSubscribers.values().iterator().next());
                        default -> new Multiple<>(newSubscribers);
                    } : this;
            }

            @Override
            void formatAndPublish(final EffectiveModelContext modelContext, final T input, final Instant now) {
                for (var entry : subscribers.asMap().entrySet()) {
                    final var formatted = format(entry.getKey(), modelContext, input, now);
                    if (formatted != null) {
                        for (var subscriber : entry.getValue()) {
                            subscriber.handler.sendDataMessage(formatted);
                        }
                    }
                }
            }
        }

        abstract @NonNull Subscribers<T> add(Subscriber<T> toAdd);

        abstract @Nullable Subscribers<T> remove(Subscriber<T> toRemove);

        abstract void formatAndPublish(EffectiveModelContext modelContext, T input, Instant now);

        @Nullable String format(final EventFormatter<T> formatter, final EffectiveModelContext modelContext,
                final T input, final Instant now) {
            try {
                return formatter.eventData(modelContext, input, now);
            } catch (Exception e) {
                // FIXME: better error handling
                throw new IllegalStateException(e);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommonSubscriber.class);

    private final EventFormatterFactory<T> formatterFactory;
    private final NotificationOutputType outputType;
    private final String streamName;
    protected final @NonNull ListenersBroker listenersBroker;

    @GuardedBy("this")
    private final Subscribers<T> subscribers = null;
    @GuardedBy("this")
    private Registration registration;

    // FIXME: NETCONF-1102: this should be tied to a subscriber
    private @NonNull EventFormatter<T> formatter;

    AbstractCommonSubscriber(final String streamName, final NotificationOutputType outputType,
            final EventFormatterFactory<T> formatterFactory, final ListenersBroker listenersBroker) {
        this.streamName = requireNonNull(streamName);
        checkArgument(!streamName.isEmpty());

        this.outputType = requireNonNull(outputType);
        this.formatterFactory = requireNonNull(formatterFactory);
        this.listenersBroker = requireNonNull(listenersBroker);
        formatter = formatterFactory.emptyFormatter();
    }

    @Override
    public final String getStreamName() {
        return streamName;
    }

    @Override
    public final String getOutputType() {
        return outputType.getName();
    }

    @Override
    public final synchronized boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    @Override
    public final synchronized Set<StreamSessionHandler> getSubscribers() {
        return new HashSet<>(subscribers);
    }

    @Override
    public final synchronized void close() throws InterruptedException, ExecutionException {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        deleteDataInDS(streamName).get();
        subscribers.clear();
    }

    @Override
    public synchronized void addSubscriber(final StreamSessionHandler subscriber) {
        final boolean isConnected = subscriber.isConnected();
        checkState(isConnected);
        LOG.debug("Subscriber {} is added.", subscriber);
        subscribers.add(subscriber);
    }

    @Override
    public synchronized void removeSubscriber(final StreamSessionHandler subscriber) {
        subscribers.remove(subscriber);
        LOG.debug("Subscriber {} is removed", subscriber);
        if (!hasSubscribers()) {
            listenersBroker.removeAndCloseListener(this);
        }
    }

    /**
     * Set query parameters for listener.
     *
     * @param params NotificationQueryParams to use.
     */
    public final void setQueryParams(final ReceiveEventsParams params) {
        final var startTime = params.startTime();
        if (startTime != null) {
            throw new RestconfDocumentedException("Stream " + streamName + " does not support replay",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var leafNodes = params.leafNodesOnly();
        final var skipData = params.skipNotificationData();
        final var changedLeafNodes = params.changedLeafNodesOnly();
        final var childNodes = params.childNodesOnly();

        final var textParams = new TextParameters(
            leafNodes != null && leafNodes.value(),
            skipData != null && skipData.value(),
            changedLeafNodes != null && changedLeafNodes.value(),
            childNodes != null && childNodes.value());

        final var filter = params.filter();
        final var filterValue = filter == null ? null : filter.paramValue();

        final EventFormatter<T> newFormatter;
        if (filterValue != null && !filterValue.isEmpty()) {
            try {
                newFormatter = formatterFactory.getFormatter(textParams, filterValue);
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Failed to get filter", e);
            }
        } else {
            newFormatter = formatterFactory.getFormatter(textParams);
        }

        // Single assign
        formatter = newFormatter;
    }

    final @NonNull EventFormatter<T> formatter() {
        return formatter;
    }

    /**
     * Sets {@link Registration} registration.
     *
     * @param registration a listener registration registration.
     */
    @Holding("this")
    final void setRegistration(final Registration registration) {
        this.registration = requireNonNull(registration);
    }

    /**
     * Checks if {@link Registration} registration exists.
     *
     * @return {@code true} if exists, {@code false} otherwise.
     */
    @Holding("this")
    final boolean isListening() {
        return registration != null;
    }

    final void formatAndPublish(final T input) {
        final var local = subscribers;
        if (local != null) {
            local.formatAndPublish(input);
        }
    }

    /**
     * Post data to subscribed SSE session handlers.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        final Iterator<StreamSessionHandler> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final StreamSessionHandler subscriber = iterator.next();
            final boolean isConnected = subscriber.isConnected();
            if (isConnected) {
                subscriber.sendDataMessage(data);
                LOG.debug("Data was sent to subscriber {} on connection {}:", this, subscriber);
            } else {
                // removal is probably not necessary, because it will be removed explicitly soon after invocation of
                // onWebSocketClosed(..) in handler; but just to be sure ...
                iterator.remove();
                LOG.debug("Subscriber for {} was removed - web-socket session is not open.", this);
            }
        }
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("stream-name", streamName).add("output-type", getOutputType());
    }
}

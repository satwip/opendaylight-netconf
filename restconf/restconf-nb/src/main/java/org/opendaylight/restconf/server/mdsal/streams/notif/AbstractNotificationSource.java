/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.notif;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;

/**
 * Abstract base class for functionality shared between {@link DOMNotification}-based sources.
 */
public abstract class AbstractNotificationSource extends Source<DOMNotification> {
    protected static final class Listener implements DOMNotificationListener {
        private final Sink<DOMNotification> sink;
        private final DatabindProvider databindProvider;

        public Listener(final Sink<DOMNotification> sink, final DatabindProvider databindProvider) {
            this.sink = requireNonNull(sink);
            this.databindProvider = requireNonNull(databindProvider);
        }

        @Override
        public void onNotification(final DOMNotification notification) {
            sink.publish(databindProvider.currentContext(), notification,
                notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        }
    }

    private static final ImmutableMap<EncodingName, NotificationFormatterFactory> ENCODINGS = ImmutableMap.of(
        EncodingName.RFC8040_JSON, JSONNotificationFormatter.FACTORY,
        EncodingName.RFC8040_XML, XMLNotificationFormatter.FACTORY);

    protected AbstractNotificationSource() {
        super(ENCODINGS);
    }
}

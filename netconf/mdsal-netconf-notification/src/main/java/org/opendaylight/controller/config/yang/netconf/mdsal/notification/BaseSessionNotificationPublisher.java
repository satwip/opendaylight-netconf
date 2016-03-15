/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdOrZeroType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEndBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStartBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Listens on changes in NetconfState/Sessions/Session datastore and publishes them
 */
public class BaseSessionNotificationPublisher extends BaseNotificationPublisher<Session> {

    private static final InstanceIdentifier<Session> INSTANCE_IDENTIFIER = InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class);
    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;

    public BaseSessionNotificationPublisher(BaseNotificationPublisherRegistration baseNotificationPublisherRegistration) {
        super(INSTANCE_IDENTIFIER, AsyncDataBroker.DataChangeScope.BASE);
        this.baseNotificationPublisherRegistration = baseNotificationPublisherRegistration;
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
        final Map<InstanceIdentifier<?>, DataObject> createdData = asyncDataChangeEvent.getCreatedData();
        createdData.values().forEach(this::publishStartedSession);

        final Set<InstanceIdentifier<?>> removedPaths = asyncDataChangeEvent.getRemovedPaths();
        final Map<InstanceIdentifier<?>, DataObject> originalData = asyncDataChangeEvent.getOriginalData();
        removedPaths.forEach((removedPath) -> publishEndedSession(originalData.get(removedPath)));
    }

    private void publishStartedSession(DataObject dataObject) {
        Preconditions.checkArgument(dataObject instanceof Session);
        Session session = (Session) dataObject;
        final NetconfSessionStart sessionStart = new NetconfSessionStartBuilder()
                .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                .setSourceHost(session.getSourceHost().getIpAddress())
                .setUsername(session.getUsername())
                .build();
        baseNotificationPublisherRegistration.onSessionStarted(sessionStart);
    }

    private void publishEndedSession(DataObject dataObject) {
        Preconditions.checkArgument(dataObject instanceof Session);
        Session session = (Session) dataObject;
        final NetconfSessionEnd sessionEnd = new NetconfSessionEndBuilder()
                .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                .setSourceHost(session.getSourceHost().getIpAddress())
                .setUsername(session.getUsername())
                .build();
        baseNotificationPublisherRegistration.onSessionEnded(sessionEnd);
    }

    @Override
    public void close() throws Exception {

    }

}

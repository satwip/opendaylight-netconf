/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import java.util.ArrayList;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notification topic registration.
 */
public abstract class NotificationTopicRegistration implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTopicRegistration.class);

    public enum NotificationSourceType {
        NetconfDeviceStream,
        ConnectionStatusChange;
    }

    private boolean active;
    private final NotificationSourceType notificationSourceType;
    private final String sourceName;
    private final String notificationUrnPrefix;
    private boolean replaySupported;

    protected NotificationTopicRegistration(NotificationSourceType notificationSourceType, String sourceName,
        String notificationUrnPrefix) {
        this.notificationSourceType = notificationSourceType;
        this.sourceName = sourceName;
        this.notificationUrnPrefix = notificationUrnPrefix;
        this.active = false;
        this.setReplaySupported(false);
    }

    public boolean isActive() {
        return active;
    }

    protected void setActive(boolean active) {
        this.active = active;
    }

    public NotificationSourceType getNotificationSourceType() {
        return notificationSourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getNotificationUrnPrefix() {
        return notificationUrnPrefix;
    }

    /**
     * Checks, if notification is from namespace belonging to this registration.
     * @param notificationPath path
     * @return true, if notification belongs to registration namespace
     */
    public boolean checkNotificationPath(SchemaPath notificationPath) {
        if (notificationPath == null) {
            return false;
        }
        String nameSpace = notificationPath.getLastComponent().getNamespace().toString();
        LOG.debug("CheckNotification - name space {} - NotificationUrnPrefix {}", nameSpace,
            getNotificationUrnPrefix());
        return nameSpace.startsWith(getNotificationUrnPrefix());
    }

    abstract void activateNotificationSource();

    abstract void deActivateNotificationSource();

    abstract void reActivateNotificationSource();

    /**
     * Registers associated event source notification to topic.
     * @param notificationPath notification path
     * @param topicId topic id
     * @return true, if successful
     */
    abstract boolean registerNotificationTopic(SchemaPath notificationPath, TopicId topicId);

    /**
     * Registers associated event source notification to topic.
     * @param topicId topic id
     * @return true, if successful
     */
    abstract void unRegisterNotificationTopic(TopicId topicId);

    /**
     * Returns registered topics for given path.
     * @param notificationPath path
     * @return topicIds
     */
    abstract ArrayList<TopicId> getNotificationTopicIds(SchemaPath notificationPath);

    public boolean isReplaySupported() {
        return replaySupported;
    }

    protected void setReplaySupported(boolean replaySupported) {
        this.replaySupported = replaySupported;
    }

}

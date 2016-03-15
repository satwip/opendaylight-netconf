/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.config.util.capability.YangModuleCapability;
import org.opendaylight.netconf.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.model.api.Module;

public class NetconfMonitoringServiceImplTest {

    private static final String TEST_MODULE_CONTENT = "content";
    private static final String TEST_MODULE_REV = "1970-01-01";
    private static final  Uri TEST_MODULE_NAMESPACE = new Uri("testModuleNamespace");
    private static final String TEST_MODULE_NAME = "testModule";
    private static final Date TEST_MODULE_DATE;

    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(1970, Calendar.JANUARY, 1);
        TEST_MODULE_DATE = calendar.getTime();
    }
    private static final Session SESSION = new SessionBuilder()
            .setSessionId(1L)
            .setSourceHost(new Host("0.0.0.0".toCharArray()))
            .setUsername("admin")
            .build();
    private int capabilitiesSize;

    private final Set<Capability> CAPABILITIES = new HashSet<>();

    @Mock
    private Module moduleMock;
    @Mock
    private NetconfOperationServiceFactory operationServiceFactoryMock;
    @Mock
    private NetconfManagementSession sessionMock;
    @Mock
    private NetconfMonitoringService.MonitoringListener listener;
    @Mock
    private BaseNotificationPublisherRegistration notificationPublisher;

    private NetconfMonitoringServiceImpl monitoringService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(new URI(TEST_MODULE_NAMESPACE.getValue())).when(moduleMock).getNamespace();
        doReturn(TEST_MODULE_NAME).when(moduleMock).getName();
        doReturn(TEST_MODULE_DATE).when(moduleMock).getRevision();
        doReturn(TEST_MODULE_NAME).when(moduleMock).getName();

        CAPABILITIES.add(new YangModuleCapability(moduleMock, TEST_MODULE_CONTENT));
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:netconf:base:1.0"));
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:netconf:base:1.1"));
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2010-09-24"));

        doReturn(CAPABILITIES).when(operationServiceFactoryMock).getCapabilities();
        doReturn(null).when(operationServiceFactoryMock).registerCapabilityListener(any(NetconfMonitoringServiceImpl.class));

        doReturn(SESSION).when(sessionMock).toManagementSession();
        doNothing().when(listener).onCapabilitiesChanged(any());
        doNothing().when(listener).onSchemasChanged(any());
        doNothing().when(listener).onSessionStarted(any());
        doNothing().when(listener).onSessionEnded(any());

        doNothing().when(notificationPublisher).onCapabilityChanged(any());
        doNothing().when(notificationPublisher).onSessionStarted(any());
        doNothing().when(notificationPublisher).onSessionEnded(any());

        monitoringService = new NetconfMonitoringServiceImpl(operationServiceFactoryMock);
        monitoringService.onCapabilitiesChanged(CAPABILITIES, Collections.emptySet());
        monitoringService.setNotificationPublisher(notificationPublisher);
        monitoringService.registerListener(listener);
        capabilitiesSize = monitoringService.getCapabilities().getCapability().size();
    }

    @Test
    public void testListeners() throws Exception {
        monitoringService.onSessionUp(sessionMock);
        HashSet<Capability> added = new HashSet<>();
        added.add(new BasicCapability("toAdd"));
        monitoringService.onCapabilitiesChanged(added, Collections.emptySet());
        monitoringService.onSessionDown(sessionMock);
        verify(listener).onSessionStarted(any());
        verify(listener).onSessionEnded(any());
        //onCapabilitiesChanged and onSchemasChanged are invoked also after listener registration
        verify(listener, times(2)).onCapabilitiesChanged(any());
        verify(listener, times(2)).onSchemasChanged(any());
    }

    @Test
    public void testGetSchemas() throws Exception {
        Schemas schemas = monitoringService.getSchemas();
        Schema schema = schemas.getSchema().get(0);
        Assert.assertEquals(TEST_MODULE_NAMESPACE, schema.getNamespace());
        Assert.assertEquals(TEST_MODULE_NAME, schema.getIdentifier());
        Assert.assertEquals(TEST_MODULE_REV, schema.getVersion());
    }

    @Test
    public void testGetSchemaForCapability() throws Exception {
        String schema = monitoringService.getSchemaForCapability(TEST_MODULE_NAME, Optional.of(TEST_MODULE_REV));
        Assert.assertEquals(TEST_MODULE_CONTENT, schema);
    }

    @Test
    public void testGetCapabilities() throws Exception {
        Capabilities actual = monitoringService.getCapabilities();
        List<Uri> exp = new ArrayList<>();
        for (Capability capability : CAPABILITIES) {
            exp.add(new Uri(capability.getCapabilityUri()));
        }
        //candidate is added by monitoring service automatically
        exp.add(0, new Uri("urn:ietf:params:netconf:capability:candidate:1.0"));
        Capabilities expected = new CapabilitiesBuilder().setCapability(exp).build();
        Assert.assertEquals(new HashSet<>(expected.getCapability()), new HashSet<>(actual.getCapability()));
    }

    @Test
    public void testClose() throws Exception {
        monitoringService.onSessionUp(sessionMock);
        Assert.assertFalse(monitoringService.getSessions().getSession().isEmpty());
        Assert.assertFalse(monitoringService.getCapabilities().getCapability().isEmpty());
        monitoringService.close();
        Assert.assertTrue(monitoringService.getSessions().getSession().isEmpty());
        Assert.assertTrue(monitoringService.getCapabilities().getCapability().isEmpty());
    }

    @Test
    public void testOnCapabilitiesChanged() throws Exception {
        final String capUri = "test";
        final Uri uri = new Uri(capUri);
        final HashSet<Capability> testCaps = new HashSet<>();
        testCaps.add(new BasicCapability(capUri));
        final ArgumentCaptor<NetconfCapabilityChange> capabilityChangeCaptor = ArgumentCaptor.forClass(NetconfCapabilityChange.class);
        final ArgumentCaptor<Capabilities> monitoringListenerCaptor = ArgumentCaptor.forClass(Capabilities.class);
        //add capability
        monitoringService.onCapabilitiesChanged(testCaps, Collections.emptySet());
        //remove capability
        monitoringService.onCapabilitiesChanged(Collections.emptySet(), testCaps);

        verify(listener, times(3)).onCapabilitiesChanged((monitoringListenerCaptor.capture()));
        verify(notificationPublisher, times(2)).onCapabilityChanged(capabilityChangeCaptor.capture());

        //verify listener calls
        final List<Capabilities> listenerValues = monitoringListenerCaptor.getAllValues();
        final List<Uri> afterRegisterState = listenerValues.get(0).getCapability();
        final List<Uri> afterAddState = listenerValues.get(1).getCapability();
        final List<Uri> afterRemoveState = listenerValues.get(2).getCapability();

        Assert.assertEquals(capabilitiesSize, afterRegisterState.size());
        Assert.assertEquals(capabilitiesSize + 1, afterAddState.size());
        Assert.assertEquals(capabilitiesSize, afterRemoveState.size());
        Assert.assertFalse(afterRegisterState.contains(uri));
        Assert.assertTrue(afterAddState.contains(uri));
        Assert.assertFalse(afterRemoveState.contains(uri));

        //verify notification publication
        final List<NetconfCapabilityChange> publisherValues = capabilityChangeCaptor.getAllValues();
        final NetconfCapabilityChange afterAdd = publisherValues.get(0);
        final NetconfCapabilityChange afterRemove = publisherValues.get(1);

        Assert.assertEquals(Collections.singleton(uri), new HashSet<>(afterAdd.getAddedCapability()));
        Assert.assertEquals(Collections.emptySet(), new HashSet<>(afterAdd.getDeletedCapability()));
        Assert.assertEquals(Collections.singleton(uri), new HashSet<>(afterRemove.getDeletedCapability()));
        Assert.assertEquals(Collections.emptySet(), new HashSet<>(afterRemove.getAddedCapability()));
    }

    @Test
    public void testOnSessionUpAndDown() throws Exception {
        monitoringService.onSessionUp(sessionMock);
        ArgumentCaptor<Session> sessionUpCaptor = ArgumentCaptor.forClass(Session.class);
        verify(listener).onSessionStarted(sessionUpCaptor.capture());
        final Session sesionUp = sessionUpCaptor.getValue();
        Assert.assertEquals(SESSION.getSessionId(), sesionUp.getSessionId());
        Assert.assertEquals(SESSION.getSourceHost(), sesionUp.getSourceHost());
        Assert.assertEquals(SESSION.getUsername(), sesionUp.getUsername());

        monitoringService.onSessionDown(sessionMock);
        ArgumentCaptor<Session> sessionDownCaptor = ArgumentCaptor.forClass(Session.class);
        verify(listener).onSessionEnded(sessionDownCaptor.capture());
        final Session sessionDown = sessionDownCaptor.getValue();
        Assert.assertEquals(SESSION.getSessionId(), sessionDown.getSessionId());
        Assert.assertEquals(SESSION.getSourceHost(), sessionDown.getSourceHost());
        Assert.assertEquals(SESSION.getUsername(), sessionDown.getUsername());
    }
}

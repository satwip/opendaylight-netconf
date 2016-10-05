/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/*
* Generated file
*
* Generated from: yang module name: netconf-northbound-notification-impl yang module local name: netconf-notification-manager
* Generated by: org.opendaylight.controller.config.yangjmxgenerator.plugin.JMXGenerator
* Generated at: Fri Aug 07 17:09:20 CEST 2015
*
* Do not modify this file unless it is present under src/main directory
*/
package org.opendaylight.controller.config.yang.netconf.northbound.notification.impl;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class NetconfNotificationManagerModuleFactory extends AbstractNetconfNotificationManagerModuleFactory {

    @Override
    public NetconfNotificationManagerModule instantiateModule(String instanceName, DependencyResolver dependencyResolver,
                                                              NetconfNotificationManagerModule oldModule, AutoCloseable oldInstance, BundleContext bundleContext) {
        NetconfNotificationManagerModule module = super.instantiateModule(instanceName, dependencyResolver, oldModule,
                oldInstance, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }

    @Override
    public NetconfNotificationManagerModule instantiateModule(String instanceName, DependencyResolver dependencyResolver,
                                                           BundleContext bundleContext) {
        NetconfNotificationManagerModule module = super.instantiateModule(instanceName, dependencyResolver, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }
}

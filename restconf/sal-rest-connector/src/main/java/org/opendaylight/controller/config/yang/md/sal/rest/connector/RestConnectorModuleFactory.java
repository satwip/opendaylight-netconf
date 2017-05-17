/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.md.sal.rest.connector;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.DynamicMBeanWithInstance;
import org.osgi.framework.BundleContext;

/**
* Generated file.
*
* <p>
* Generated from: yang module name: opendaylight-rest-connector yang module local name: rest-connector-impl
* Generated by: org.opendaylight.controller.config.yangjmxgenerator.plugin.JMXGenerator
* Generated at: Fri Jul 25 04:33:31 CDT 2014
*
* <p>
* Do not modify this file unless it is present under src/main directory
*/
public class RestConnectorModuleFactory
        extends org.opendaylight.controller.config.yang.md.sal.rest.connector.AbstractRestConnectorModuleFactory {

    @Override
    public RestConnectorModule instantiateModule(final String instanceName, final DependencyResolver dependencyResolver,
                                                 final BundleContext bundleContext) {
        final RestConnectorModule restConnectorModule = super.instantiateModule(instanceName,
                dependencyResolver, bundleContext);
        restConnectorModule.setBundleContext(bundleContext);
        return restConnectorModule;
    }

    @Override
    public RestConnectorModule instantiateModule(final String instanceName, final DependencyResolver dependencyResolver,
                                                 final RestConnectorModule oldModule, final AutoCloseable oldInstance,
                                                 final BundleContext bundleContext) {
        final RestConnectorModule restConnectorModule = super.instantiateModule(instanceName,
                dependencyResolver, oldModule, oldInstance, bundleContext);
        restConnectorModule.setBundleContext(bundleContext);
        return restConnectorModule;
    }

    @Override
    public RestConnectorModule handleChangedClass(final DependencyResolver dependencyResolver,
                                                  final DynamicMBeanWithInstance old, final BundleContext bundleContext)
            throws Exception {
        //close old restconf instance
        old.getModule().getInstance().close();
        final RestConnectorModule restconfModule = super.handleChangedClass(dependencyResolver, old, bundleContext);
        restconfModule.setBundleContext(bundleContext);
        return restconfModule;
    }
}

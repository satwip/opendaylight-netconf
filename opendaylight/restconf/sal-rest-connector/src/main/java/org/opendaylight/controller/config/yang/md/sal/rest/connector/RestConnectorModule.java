package org.opendaylight.controller.config.yang.md.sal.rest.connector;

import org.opendaylight.netconf.sal.restconf.impl.RestconfProviderImpl;


public class RestConnectorModule extends org.opendaylight.controller.config.yang.md.sal.rest.connector.AbstractRestConnectorModule {

    private static RestConnectorRuntimeRegistration runtimeRegistration;

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestConnectorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // Create an instance of our provider
        RestconfProviderImpl instance = new RestconfProviderImpl();
        // Set its port
        instance.setWebsocketPort(getWebsocketPort());
        // Register it with the Broker
        getDomBrokerDependency().registerProvider(instance);

        if(runtimeRegistration != null){
            runtimeRegistration.close();
        }

        runtimeRegistration =
            getRootRuntimeBeanRegistratorWrapper().register(instance);

        return instance;
    }
}


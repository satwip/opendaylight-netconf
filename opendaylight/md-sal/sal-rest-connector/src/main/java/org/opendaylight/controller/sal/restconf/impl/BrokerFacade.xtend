package org.opendaylight.controller.sal.restconf.impl

import javax.ws.rs.core.Response
import org.opendaylight.controller.md.sal.common.api.data.DataReader
import org.opendaylight.controller.sal.core.api.Broker.ConsumerSession
import org.opendaylight.controller.sal.core.api.data.DataBrokerService
import org.opendaylight.controller.sal.rest.impl.RestconfProvider
import org.opendaylight.yangtools.yang.common.QName
import org.opendaylight.yangtools.yang.common.RpcResult
import org.opendaylight.yangtools.yang.data.api.CompositeNode
import org.opendaylight.yangtools.yang.data.api.InstanceIdentifier
import org.slf4j.LoggerFactory

class BrokerFacade implements DataReader<InstanceIdentifier, CompositeNode> {


    val static LOG = LoggerFactory.getLogger(BrokerFacade)
    val static BrokerFacade INSTANCE = new BrokerFacade

    @Property
    private ConsumerSession context;

    @Property
    private DataBrokerService dataService;
    
    private new() {
        if (INSTANCE !== null) {
            throw new IllegalStateException("Already instantiated");
        }
    }

    def static BrokerFacade getInstance() {
        return INSTANCE
    }

    private def void checkPreconditions() {
        if (context === null || dataService === null) {
            throw new ResponseException(Response.Status.SERVICE_UNAVAILABLE, RestconfProvider::NOT_INITALIZED_MSG)
        }
    }

    override readConfigurationData(InstanceIdentifier path) {
        checkPreconditions
        LOG.info("Read Configuration via Restconf: {}", path)
        return dataService.readConfigurationData(path);
    }

    override readOperationalData(InstanceIdentifier path) {
        checkPreconditions
        LOG.info("Read Operational via Restconf: {}", path)
        return dataService.readOperationalData(path);
    }

    def RpcResult<CompositeNode> invokeRpc(QName type, CompositeNode payload) {
        checkPreconditions
        val future = context.rpc(type, payload);
        return future.get;
    }

    def commitConfigurationDataPut(InstanceIdentifier path, CompositeNode payload) {
        checkPreconditions
        val transaction = dataService.beginTransaction;
        LOG.info("Put Configuration via Restconf: {}", path)
        transaction.putConfigurationData(path, payload);
        return transaction.commit()
    }

    def commitConfigurationDataPost(InstanceIdentifier path, CompositeNode payload) {
        checkPreconditions
        val transaction = dataService.beginTransaction;
        transaction.putConfigurationData(path, payload);
        if (payload == transaction.createdConfigurationData.get(path)) {
            LOG.info("Post Configuration via Restconf: {}", path)
            return transaction.commit()
        }
        LOG.info("Post Configuration via Restconf was not executed because data already exists: {}", path)
        return null;
    }

}

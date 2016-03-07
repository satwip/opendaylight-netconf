/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.math.BigInteger;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.services.ServicesWrapper;

public class ServicesWrapperImpl implements ServicesWrapper {

    @Override
    public NormalizedNodeContext readData(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Response deleteData(final String identifier) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getConfigGet() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getSuccessGetConfig() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getFailureGetConfig() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getConfigPost() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getSuccessPost() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getFailurePost() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getConfigPut() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getSuccessPut() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getFailurePut() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getConfigDelete() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getSuccessDelete() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getFailureDelete() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getOperationalGet() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getSuccessGetOperational() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getFailureGetOperational() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigInteger getRpc() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

}

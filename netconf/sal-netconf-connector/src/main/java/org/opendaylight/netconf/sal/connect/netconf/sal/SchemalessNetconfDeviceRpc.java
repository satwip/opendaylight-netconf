/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Function;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to CompositeNode.
 */
public final class SchemalessNetconfDeviceRpc implements DOMRpcService {

    private static final Logger LOG= LoggerFactory.getLogger(SchemalessNetconfDeviceRpc.class);
    private final RemoteDeviceCommunicator<NetconfMessage> listener;
    private final BaseRpcSchemalessTransformer baseRpcTransformer;
    private final SchemalessMessageTransformer schemalessTransformer;
    private final RemoteDeviceId deviceId;

    public SchemalessNetconfDeviceRpc(RemoteDeviceId deviceId, final RemoteDeviceCommunicator<NetconfMessage> listener) {
        this.deviceId = deviceId;
        this.listener = listener;
        final MessageCounter counter = new MessageCounter();
        baseRpcTransformer = new BaseRpcSchemalessTransformer(counter);
        schemalessTransformer = new SchemalessMessageTransformer(counter);
    }

    @Nonnull
    @Override
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(@Nonnull final SchemaPath type, @Nullable final NormalizedNode<?, ?> input) {
        final MessageTransformer<NetconfMessage> transformer;
        if (input instanceof AnyXmlNode) {
            transformer = schemalessTransformer;
        } else if (isBaseRpc(type)) {
            transformer = baseRpcTransformer;
        } else {
            return Futures.immediateFailedCheckedFuture(new DOMRpcImplementationNotAvailableException("Unable to invoke rpc %s", type));
        }
        return handleRpc(type, input, transformer);
    }

    private CheckedFuture<DOMRpcResult, DOMRpcException> handleRpc(@Nonnull final SchemaPath type,
                                                                   @Nullable NormalizedNode<?, ?> input,
                                                                   MessageTransformer<NetconfMessage> transformer) {
        final NetconfMessage netconfMessage = transformer.toRpcRequest(type, input);
        final ListenableFuture<RpcResult<NetconfMessage>> rpcResultListenableFuture = listener.sendRequest(netconfMessage, type.getLastComponent());

        final ListenableFuture<DOMRpcResult> transformed = Futures.transform(rpcResultListenableFuture, new Function<RpcResult<NetconfMessage>, DOMRpcResult>() {
            @Override
            public DOMRpcResult apply(final RpcResult<NetconfMessage> input) {
                if (input.isSuccessful()) {
                    return transformer.toRpcResult(input.getResult(), type);
                } else {
                    return new DefaultDOMRpcResult(input.getErrors());
                }
            }
        });

        return Futures.makeChecked(transformed, new Function<Exception, DOMRpcException>() {
            @Nullable
            @Override
            public DOMRpcException apply(@Nullable final Exception e) {
                return new DOMRpcImplementationNotAvailableException(e, "Unable to invoke rpc %s on device %s", type, deviceId);
            }
        });
    }


    private boolean isBaseRpc(SchemaPath type) {
        return NetconfMessageTransformUtil.NETCONF_URI.equals(type.getLastComponent().getNamespace());
    }

    @Nonnull
    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(@Nonnull final T listener) {
        throw new UnsupportedOperationException("Not available for netconf 1.0");
    }

}

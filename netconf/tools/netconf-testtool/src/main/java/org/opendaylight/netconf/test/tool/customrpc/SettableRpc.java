/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.customrpc;

import java.io.File;
import java.util.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.w3c.dom.Document;

/**
 * {@link NetconfOperation} implementation. It can be configured to intercept rpcs with defined input
 * and reply with defined output. If input isn't defined, rpc handling is delegated to the subsequent
 * {@link NetconfOperation} which is able to handle it.
 */
class SettableRpc implements NetconfOperation {

    private final RpcMapping mapping;

    SettableRpc(final File rpcConfig) {
        mapping = new RpcMapping(rpcConfig);
    }

    @Override
    public HandlingPriority canHandle(final Document message) throws DocumentedException {
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY.increasePriority(1000);
    }

    @Override
    public Document handle(final Document requestMessage, final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
        final XmlElement requestElement = XmlElement.fromDomDocument(requestMessage);
        final XmlElement rpcElement = requestElement.getOnlyChildElement();
        final String msgId = requestElement.getAttribute(XmlNetconfConstants.MESSAGE_ID);
        final Optional<Document> response = mapping.getResponse(rpcElement);
        if (response.isPresent()) {
            final Document document = response.get();
            document.getDocumentElement().setAttribute(XmlNetconfConstants.MESSAGE_ID, msgId);
            return document;
        } else if (subsequentOperation.isExecutionTermination()) {
            throw new DocumentedException("Mapping not found " + XmlUtil.toString(requestMessage),
                    DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_not_supported,
                    DocumentedException.ErrorSeverity.error);
        } else {
            return subsequentOperation.execute(requestMessage);
        }
    }

}

/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A {@link ServerDataOperations} implementation fails all requests with {@link ErrorTag#OPERATION_NOT_SUPPORTED}.
 */
@NonNullByDefault
public final class NotSupportedServerDataOperations implements ServerDataOperations {
    public static final NotSupportedServerDataOperations INSTANCE = new NotSupportedServerDataOperations();

    private NotSupportedServerDataOperations() {
        // Hidden on purpose
    }

    @Override
    public void createData(final ServerRequest<? super CreateResourceResult> request, final YangInstanceIdentifier path,
            final NormalizedNode data) {
        notSupported(request);
    }

    @Override
    public void createData(final ServerRequest<? super CreateResourceResult> request, final YangInstanceIdentifier path,
            final Insert insert, final NormalizedNode data) {
        notSupported(request);
    }

    @Override
    public void deleteData(final ServerRequest<Empty> request, final YangInstanceIdentifier path) {
        notSupported(request);
    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        notSupported(request);
    }

    @Override
    public void mergeData(final ServerRequest<DataPatchResult> request, final YangInstanceIdentifier path,
            final NormalizedNode data) {
        notSupported(request);
    }

    @Override
    public void patchData(final ServerRequest<DataYangPatchResult> request, final YangInstanceIdentifier path,
            final PatchContext patch) {
        notSupported(request);
    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final YangInstanceIdentifier path,
            final NormalizedNode data) {
        notSupported(request);
    }

    @Override
    public void putData(final ServerRequest<DataPutResult> request, final YangInstanceIdentifier path,
            final Insert insert, final NormalizedNode data) {
        notSupported(request);
    }

    private static void notSupported(final ServerRequest<?> request) {
        request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Data request not supported"));
    }
}

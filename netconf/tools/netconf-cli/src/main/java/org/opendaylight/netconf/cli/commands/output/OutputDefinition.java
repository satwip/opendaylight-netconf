/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.commands.output;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Iterator;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * The definition of output elements represented by schema nodes parsed from yang rpc definition
 */
public class OutputDefinition implements Iterable<DataSchemaNode> {

    public static final OutputDefinition EMPTY_OUTPUT = new OutputDefinition(Collections.emptySet());
    private final Iterable<DataSchemaNode> childNodes;

    public OutputDefinition(final Iterable<DataSchemaNode> childNodes) {
        this.childNodes = childNodes;
    }

    @Override
    public Iterator<DataSchemaNode> iterator() {
        return childNodes.iterator();
    }

    public static OutputDefinition fromOutput(final ContainerSchemaNode output) {
        Preconditions.checkNotNull(output);
        return new OutputDefinition(output.getChildNodes());
    }

    public static OutputDefinition empty() {
        return EMPTY_OUTPUT;
    }

    public boolean isEmpty() {
        return this == EMPTY_OUTPUT;
    }

}

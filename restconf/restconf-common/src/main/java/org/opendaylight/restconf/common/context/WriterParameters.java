/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

@Deprecated(forRemoval = true, since = "2.0.6")
public class WriterParameters {
    static final WriterParameters EMPTY = new WriterParametersBuilder().build();

    private final Integer depth;
    private final boolean prettyPrint;

    protected WriterParameters(final WriterParametersBuilder builder) {
        depth = builder.depth;
        prettyPrint = builder.prettyPrint;
    }

    public Integer getDepth() {
        return depth;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public static class WriterParametersBuilder {
        private Integer depth;
        private boolean prettyPrint;

        public WriterParametersBuilder() {

        }

        public WriterParametersBuilder setDepth(final int depth) {
            this.depth = depth;
            return this;
        }

        public WriterParametersBuilder setPrettyPrint(final boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public WriterParameters build() {
            return new WriterParameters(this);
        }
    }
}

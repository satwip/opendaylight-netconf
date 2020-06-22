/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.namespace.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Holder of XPath context for get or get-config RPC.
 *
 * @param <T> specific type for path
 *
 */
public class NetconfXPathContext<T> {

    public static final String XMLNS = "xmlns";
    private static final String BASE_NS_PREFIX = "nxpcrpc";

    private final List<QName> namespaces;
    private final AtomicInteger atomicInteger;

    private String xpathWithNSPrefixes;
    private Optional<T> path = Optional.empty();

    public NetconfXPathContext(String xpath) {
        namespaces = new ArrayList<>();
        xpathWithNSPrefixes = xpath;
        atomicInteger = new AtomicInteger(0);
    }

    public synchronized void addNamespace(String namespace) {
        final String namespacePrefix = new StringBuilder(BASE_NS_PREFIX)
                .append(atomicInteger.getAndIncrement())
                .toString();
        mapNamespace(namespacePrefix, namespace);

        namespaces.add(new QName(XMLNS, namespacePrefix, namespace));
    }

    private void mapNamespace(String namespacePrefix, String namespace) {
        xpathWithNSPrefixes = xpathWithNSPrefixes.replace(namespace, namespacePrefix);
    }

    public List<QName> getNamespaces() {
        return Collections.unmodifiableList(namespaces);
    }

    public String getXpathWithPrefixes() {
        return xpathWithNSPrefixes;
    }

    public static NetconfXPathContext<YangInstanceIdentifier> empty() {
        final NetconfXPathContext<YangInstanceIdentifier> netconfXPathContext = new NetconfXPathContext<>("/*");
        netconfXPathContext.setPath(YangInstanceIdentifier.empty());
        return netconfXPathContext;
    }

    public void setPath(T path) {
        this.path = Optional.of(path);
    }

    public Optional<T> getPath() {
        return path;
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.messages;

import com.google.common.base.Optional;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.util.mapping.AbstractNetconfOperation.OperationNameAndNamespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * See <a href="http://tools.ietf.org/html/rfc6241#section-6">rfc6241</a> for details.
 */
public class SubtreeFilter {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilter.class);

    public static Document applyRpcSubtreeFilter(final Document requestDocument,
                                                 final Document rpcReply) throws DocumentedException {
        final OperationNameAndNamespace operationNameAndNamespace = new OperationNameAndNamespace(requestDocument);
        if (XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0.equals(operationNameAndNamespace.getNamespace())
                && XmlNetconfConstants.GET.equals(operationNameAndNamespace.getOperationName())
                || XmlNetconfConstants.GET_CONFIG.equals(operationNameAndNamespace.getOperationName())) {
            // process subtree filtering here, in case registered netconf operations do
            // not implement filtering.
            final Optional<XmlElement> maybeFilter = operationNameAndNamespace.getOperationElement()
                    .getOnlyChildElementOptionally(XmlNetconfConstants.FILTER,
                            XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
            if (!maybeFilter.isPresent()) {
                return rpcReply;
            }
            final XmlElement filter = maybeFilter.get();
            if (isSupported(filter)) {

                // do
                return filtered(maybeFilter.get(), rpcReply);
            }
        }

        return rpcReply; // return identical document
    }

    /**
     * Filters notification content. If filter type isn't of type "subtree", returns unchanged notification content.
     * If no match is found, absent is returned.
     * @param filter filter
     * @param notification notification
     * @return document containing filtered notification content
     * @throws DocumentedException if operation fails
     */
    public static Optional<Document> applySubtreeNotificationFilter(final XmlElement filter,
                                                                    final Document notification)
            throws DocumentedException {
        removeEventTimeNode(notification);
        if (isSupported(filter)) {
            return Optional.fromNullable(filteredNotification(filter, notification));
        }
        return Optional.of(extractNotificationContent(notification));
    }

    private static void removeEventTimeNode(final Document document) {
        final Node eventTimeNode = document.getDocumentElement().getElementsByTagNameNS(XmlNetconfConstants
                .URN_IETF_PARAMS_NETCONF_CAPABILITY_NOTIFICATION_1_0, XmlNetconfConstants.EVENT_TIME).item(0);
        document.getDocumentElement().removeChild(eventTimeNode);
    }

    private static boolean isSupported(final XmlElement filter) {
        return "subtree".equals(filter.getAttribute("type"))
                || "subtree".equals(filter.getAttribute("type",
                XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0));
    }

    private static Document extractNotificationContent(final Document notification) throws DocumentedException {
        final XmlElement root = XmlElement.fromDomElement(notification.getDocumentElement());
        final XmlElement content = root.getOnlyChildElement();
        notification.removeChild(root.getDomElement());
        notification.appendChild(content.getDomElement());
        return notification;
    }

    private static Document filteredNotification(final XmlElement filter,
                                                 final Document originalNotification) throws DocumentedException {
        final Document result = XmlUtil.newDocument();
        final XmlElement dataSrc = XmlElement.fromDomDocument(originalNotification);
        final Element dataDst = (Element) result.importNode(dataSrc.getDomElement(), false);
        for (final XmlElement filterChild : filter.getChildElements()) {
            addSubtree2(filterChild, dataSrc.getOnlyChildElement(), XmlElement.fromDomElement(dataDst));
        }
        if (dataDst.getFirstChild() != null) {
            result.appendChild(dataDst.getFirstChild());
            return result;
        } else {
            return null;
        }
    }

    private static Document filtered(final XmlElement filter, final Document originalReplyDocument)
            throws DocumentedException {
        final Document result = XmlUtil.newDocument();
        // even if filter is empty, copy /rpc/data
        final Element rpcReply = originalReplyDocument.getDocumentElement();
        final Node rpcReplyDst = result.importNode(rpcReply, false);
        result.appendChild(rpcReplyDst);
        final XmlElement dataSrc = XmlElement.fromDomElement(rpcReply).getOnlyChildElement("data",
                XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        final Element dataDst = (Element) result.importNode(dataSrc.getDomElement(), false);
        rpcReplyDst.appendChild(dataDst);
        addSubtree(filter, dataSrc, XmlElement.fromDomElement(dataDst));

        return result;
    }

    public static Element filtered(final XmlElement filter,
                                   final Element originalReplyDocument) throws DocumentedException {
        if (filter == null) {
            return originalReplyDocument;
        }
        //TODO - remove XmlUtil.newDocument(), so no import node needed
        final Element resultElement = XmlUtil.newDocument().createElement("result");
        final XmlElement dataSrc = XmlElement.fromDomElement(originalReplyDocument);
        final XmlElement dst = XmlElement.fromDomElement(resultElement);
        for (final XmlElement srcChild : dataSrc.getChildElements()) {
            addSubtree2(filter, srcChild, dst);
        }
        return resultElement;
    }

    private static void addSubtree(final XmlElement filter, final XmlElement src, final XmlElement dst)
            throws DocumentedException {
        for (final XmlElement srcChild : src.getChildElements()) {
            for (final XmlElement filterChild : filter.getChildElements()) {
                addSubtree2(filterChild, srcChild, dst);
            }
        }
    }

    private static MatchingResult addSubtree2(final XmlElement filter, final XmlElement src,
                                              final XmlElement dstParent) throws DocumentedException {
        final Document document = dstParent.getDomElement().getOwnerDocument();
        final MatchingResult matches = matches(src, filter);
        if (matches != MatchingResult.NO_MATCH && matches != MatchingResult.CONTENT_MISMATCH) {
            // copy srcChild to dst
            final boolean filterHasChildren = filter.getChildElements().isEmpty() == false;
            // copy to depth if this is leaf of filter tree
            Element copied = (Element) document.importNode(src.getDomElement(), filterHasChildren == false);
            boolean shouldAppend = filterHasChildren == false;
            if (filterHasChildren) { // this implies TAG_MATCH
                // do the same recursively
                int numberOfTextMatchingChildren = 0;
                for (final XmlElement srcChild : src.getChildElements()) {
                    for (final XmlElement filterChild : filter.getChildElements()) {
                        final MatchingResult childMatch =
                                addSubtree2(filterChild, srcChild, XmlElement.fromDomElement(copied));
                        if (childMatch == MatchingResult.CONTENT_MISMATCH) {
                            return MatchingResult.NO_MATCH;
                        }
                        if (childMatch == MatchingResult.CONTENT_MATCH) {
                            numberOfTextMatchingChildren++;
                        }
                        shouldAppend |= childMatch != MatchingResult.NO_MATCH;
                    }
                }
                // if only text matching child filters are specified..
                if (numberOfTextMatchingChildren == filter.getChildElements().size()) {
                    // force all children to be added (to depth). This is done by copying parent node to depth.
                    // implies shouldAppend == true
                    copied = (Element) document.importNode(src.getDomElement(), true);
                }
            }
            if (shouldAppend) {
                dstParent.getDomElement().appendChild(copied);
            }
        }
        return matches;
    }

    /**
     * Shallow compare src node to filter: tag name and namespace must match.
     * If filter node has no children and has text content, it also must match.
     */
    private static MatchingResult matches(final XmlElement src, final XmlElement filter) throws DocumentedException {
        final boolean tagMatch = src.getName().equals(filter.getName())
                && src.getNamespaceOptionally().equals(filter.getNamespaceOptionally());
        MatchingResult result = null;
        if (tagMatch) {
            // match text content
            final Optional<String> maybeText = filter.getOnlyTextContentOptionally();
            if (maybeText.isPresent()) {
                if (maybeText.equals(src.getOnlyTextContentOptionally()) || prefixedContentMatches(filter, src)) {
                    result = MatchingResult.CONTENT_MATCH;
                } else {
                    result = MatchingResult.CONTENT_MISMATCH;
                }
            }
            // match attributes, combination of content and tag is not supported
            if (result == null) {
                for (final Attr attr : filter.getAttributes().values()) {
                    // ignore namespace declarations
                    if (XmlUtil.XMLNS_URI.equals(attr.getNamespaceURI()) == false) {
                        // find attr with matching localName(),  namespaceURI(),  == value() in src
                        final String found = src.getAttribute(attr.getLocalName(), attr.getNamespaceURI());
                        if (attr.getValue().equals(found) && result != MatchingResult.NO_MATCH) {
                            result = MatchingResult.TAG_MATCH;
                        } else {
                            result = MatchingResult.NO_MATCH;
                        }
                    }
                }
            }
            if (result == null) {
                result = MatchingResult.TAG_MATCH;
            }
        }
        if (result == null) {
            result = MatchingResult.NO_MATCH;
        }
        LOG.debug("Matching {} to {} resulted in {}", src, filter, result);
        return result;
    }

    private static boolean prefixedContentMatches(final XmlElement filter,
                                                  final XmlElement src) throws DocumentedException {
        final Map.Entry<String, String> prefixToNamespaceOfFilter;
        final Map.Entry<String, String> prefixToNamespaceOfSrc;
        try {
            prefixToNamespaceOfFilter = filter.findNamespaceOfTextContent();
            prefixToNamespaceOfSrc = src.findNamespaceOfTextContent();
        } catch (final IllegalArgumentException e) {
            //if we can't find namespace of prefix - it's not a prefix, so it doesn't match
            return false;
        }

        final String prefix = prefixToNamespaceOfFilter.getKey();
        // If this is not a prefixed content, we do not need to continue since content do not match
        if (prefix.equals(XmlElement.DEFAULT_NAMESPACE_PREFIX)) {
            return false;
        }
        // Namespace mismatch
        if (!prefixToNamespaceOfFilter.getValue().equals(prefixToNamespaceOfSrc.getValue())) {
            return false;
        }

        final String unprefixedFilterContent =
                filter.getTextContent().substring(prefixToNamespaceOfFilter.getKey().length() + 1);
        final String unprefixedSrcContnet =
                src.getTextContent().substring(prefixToNamespaceOfSrc.getKey().length() + 1);
        // Finally compare unprefixed content
        return unprefixedFilterContent.equals(unprefixedSrcContnet);
    }

    enum MatchingResult {
        NO_MATCH, TAG_MATCH, CONTENT_MATCH, CONTENT_MISMATCH
    }
}

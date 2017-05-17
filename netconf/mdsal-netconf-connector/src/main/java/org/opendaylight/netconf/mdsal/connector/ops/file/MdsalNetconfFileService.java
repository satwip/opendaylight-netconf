/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.config.yang.netconf.mdsal.mapper.FolderWhiteList;
import org.w3c.dom.Document;

public class MdsalNetconfFileService implements NetconfFileService {
    private List<File> readOnlyList = new ArrayList<>();
    private List<File> readWriteList = new ArrayList<>();

    public MdsalNetconfFileService(FolderWhiteList folderWhiteList) {
        if (folderWhiteList != null) {
            if (folderWhiteList.getReadOnlyList() != null) {
                readOnlyList = folderWhiteList.getReadOnlyList().stream()
                        .map(File::new)
                        .collect(Collectors.toList());
            }

            if (folderWhiteList.getReadWriteList() != null) {
                readWriteList = folderWhiteList.getReadWriteList().stream()
                        .map(File::new)
                        .collect(Collectors.toList());
            }
        }
    }

    @Override
    public boolean canRead(File file) {
        if (canWrite(file)) {
            return true;
        }
        return readOnlyList.stream().anyMatch(whiteFolder -> isSubDirectory(file, whiteFolder));
    }

    @Override
    public boolean canWrite(File file) {
        return readWriteList.stream().anyMatch(whiteFolder -> isSubDirectory(file, whiteFolder));
    }

    @Override
    public XmlElement readContent(File file) throws Exception {
        final Document document = XmlUtil.readXmlToDocument(new FileInputStream(file));
        return XmlElement.fromDomDocument(document);
    }

    @Override
    public void writeContentToFile(File file, Document document) throws TransformerException, IOException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        OutputStream fileInputStream = new FileOutputStream(file);
        transformer.transform(new DOMSource(document), new StreamResult(fileInputStream));

        fileInputStream.flush();
        fileInputStream.close();
    }


    @Override
    public boolean deleteFile(File file) {
        return file.delete();
    }

    /**
     * Checks, whether the child directory is a subdirectory of the base
     * directory.
     *
     * @param base the base directory.
     * @param child the suspected child directory.
     * @return true, if the child is a subdirectory of the base directory.
     */
    public boolean isSubDirectory(File base, File child)  {
        try {
            base = base.getCanonicalFile();
            child = child.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }


        File parentFile = base;
        while (parentFile != null) {
            if (child.equals(parentFile)) {
                return true;
            }
            parentFile = parentFile.getParentFile();
        }
        return false;
    }
}

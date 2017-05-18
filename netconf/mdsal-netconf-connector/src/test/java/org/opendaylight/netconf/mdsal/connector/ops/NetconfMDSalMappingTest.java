/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.config.yang.netconf.mdsal.mapper.FolderWhiteList;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.file.MdsalNetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.get.Get;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetconfMDSalMappingTest extends AbstractNetconfMDSalTest {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMDSalMappingTest.class);

    private static final String RPC_REPLY_ELEMENT = "rpc-reply";
    private static final String DATA_ELEMENT = "data";
    private static final String FILTER_NODE = "filter";
    private static final String GET_CONFIG = "get-config";
    private static final QName TOP = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "top");
    private static final QName USERS = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "users");
    private static final QName USER = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "user");
    private static final QName MODULES = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "modules");
    private static final QName AUGMENTED_CONTAINER = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "augmented-container");
    private static final QName AUGMENTED_STRING_IN_CONT = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "identifier");
    private static final QName CHOICE_NODE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "choice-node");
    private static final QName AUGMENTED_CASE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "augmented-case");
    private static final QName CHOICE_WRAPPER = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "choice-wrapper");
    private static final QName INNER_CHOICE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "inner-choice");
    private static final QName INNER_CHOICE_TEXT = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "text");

    private static final YangInstanceIdentifier AUGMENTED_CONTAINER_IN_MODULES =
            YangInstanceIdentifier.builder().node(TOP).node(MODULES).build();

    private static Document RPC_REPLY_OK = null;

    static {
        try {
            RPC_REPLY_OK = XmlFileLoader.xmlFileToDocument("messages/mapping/rpc-reply_ok.xml");
        } catch (Exception e) {
            LOG.debug("unable to load rpc reply ok.", e);
            RPC_REPLY_OK = XmlUtil.newDocument();
        }
    }

    private CurrentSchemaContext currentSchemaContext = null;
    private SchemaContext schemaContext = null;
    private String sessionIdForReporting = "netconf-test-session1";

    private TransactionProvider transactionProvider = null;
    private NetconfFileService netconfFileService = null;

    @Mock
    private SchemaSourceProvider<YangTextSchemaSource> sourceProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        this.schemaContext = parseYangStreams(getYangSchemas());
        schemaContext.getModules();
        final SchemaService schemaService = createSchemaService();

        final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

        final EnumMap<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
                16, 16, "CommitFutures");

        final ConcurrentDOMDataBroker cdb = new ConcurrentDOMDataBroker(datastores, listenableFutureExecutor);
        this.transactionProvider = new TransactionProvider(cdb, sessionIdForReporting);

        doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final SourceIdentifier sId = (SourceIdentifier) invocationOnMock.getArguments()[0];
                final YangTextSchemaSource yangTextSchemaSource =
                        YangTextSchemaSource.delegateForByteSource(sId, ByteSource.wrap("module test".getBytes()));
                return Futures.immediateCheckedFuture(yangTextSchemaSource);

            }
        }).when(sourceProvider).getSource(any(SourceIdentifier.class));

        this.currentSchemaContext = new CurrentSchemaContext(schemaService, sourceProvider);

        // define the path, where can read and write

        FolderWhiteList folderWhiteList = new FolderWhiteList();
        folderWhiteList.setReadOnlyList(Lists.newArrayList(createPathFromClassLoader("messages/mapping/files/read-only/config_element.xml")));
        folderWhiteList.setReadWriteList(Lists.newArrayList(createPathFromClassLoader("messages/mapping/files/read-write/config_element.xml")));
        this.netconfFileService = new MdsalNetconfFileService(folderWhiteList);

    }

    @Test
    public void testEmptyDatastore() throws Exception {
        assertEmptyDatastore(get());
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());
    }

    @Test
    public void testIncorrectGet() throws Exception {

        try {
            executeOperation(new GetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider), "messages/mapping/bad_getConfig.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            executeOperation(new GetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider), "messages/mapping/bad_namespace_getConfig.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }


    }

    @Test
    public void testEditRunning() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_running.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));
    }

    @Test
    public void testEditRunningTestOnly() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_running_test_only.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigRunning());
    }

    @Test
    public void testCommitWithoutOpenTransaction() throws Exception {
        verifyResponse(commit(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testCandidateTransaction() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(discardChanges(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());

    }

    @Test
    public void testEditWithCommit() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        deleteDatastore();

    }

    @Test
    public void testEditWithTestOnly() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_test_only.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testEditContinueOnError() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_continueOnError.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));
    }

    @Test
    public void testEditRollbackOnError() throws Exception {
        try {
            edit("messages/mapping/editConfigs/editConfig_rollbackOnError.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.application);
        }
    }

    @Test
    public void testValidateCandidate() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        verifyResponse(validate("messages/mapping/validate/validate_candidate.xml"), RPC_REPLY_OK);

    }

    @Test
    public void testValidateRunning() throws Exception {
        try {
            validate("messages/mapping/validate/validate_running.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }
    }

    @Test
    public void testIncorrectValidate() throws Exception {
        try {
            validate("messages/mapping/validate/validate_empty.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.invalid_value);
            assertTrue(e.getErrorType() == ErrorType.application);
        }

        try {
            validate("messages/mapping/validate/validate_datasource_and_config_element.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.application);
        }
    }

    @Test
    public void testValidateEmfptyTransaction() throws Exception {
        verifyResponse(validate("messages/mapping/validate/validate_candidate.xml"), RPC_REPLY_OK);
    }


    @Test
    public void testValidateConfigElement() throws Exception {
        verifyResponse(validate("messages/mapping/validate/validate_config_element_correct.xml"), RPC_REPLY_OK);
    }

    @Test
    public void testValidateIncorrectConfigElement() throws Exception {
        // test incorrect module
        try {
            validate("messages/mapping/validate/validate_config_element_incorrect_module.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        // test incorrect element in module
        try {
            validate("messages/mapping/validate/validate_config_element_incorrect_element.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    // Validation can't detect whether the value of the element is correct
    @Test
    @Ignore
    public void testValidateIncorrectElementValueConfigElement() throws Exception {
        // test incorrect element value
        try {
            validate("messages/mapping/validate/validate_config_element_incorrect_value.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    @Test
    public void testValidateFromFile() throws Exception {
        verifyResponse(validateIncludeURL("messages/mapping/files/validate_read_only.xml"), RPC_REPLY_OK);
    }

    @Test
    public void testValidateFromFileIncorrect() throws Exception {
        try {
            validateIncludeURL("messages/mapping/files/validate_blacklist.xml");
            fail("Validate should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            validateIncludeURL("messages/mapping/files/validate_file_not_exist.xml");
            fail("Validate should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            validateIncludeURL("messages/mapping/files/validate_url_empty.xml");
            fail("Validate should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    private void assertOperationFailed(DocumentedException e) {
        assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
        assertTrue(e.getErrorTag() == ErrorTag.operation_failed);
        assertTrue(e.getErrorType() == ErrorType.application);
    }

    @Test
    public void testKeyOrder() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_keys_1.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        final Document configRunning = getConfigRunning();
        final String responseAsString = XmlUtil.toString(configRunning);
        verifyResponse(configRunning, XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_keys_1_control.xml"));

        final int key3 = responseAsString.indexOf("key3");
        final int key1 = responseAsString.indexOf("key1");
        final int key2 = responseAsString.indexOf("key2");

        assertTrue(String.format("Key ordering invalid, should be key3(%d) < key1(%d) < key2(%d)", key3, key1, key2),
                key3 < key1 && key1 < key2);

        deleteDatastore();
    }


    @Test
    public void testMultipleEditsWithMerge() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_control_1.xml"));
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_single_1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_control_2.xml"));
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_control_2.xml"));

        deleteDatastore();

    }

    @Test
    public void testMoreComplexEditConfigs() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_1.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_single_1.xml"), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_2.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_after_more_complex_merge.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_3.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_after_more_complex_merge_2.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_4_replace.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_after_replace.xml"));
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_after_replace.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_replace_default.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_replace_default_control.xml"));
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_replace_default_control.xml"));

        deleteDatastore();

    }

    @Test
    public void testLock() throws Exception {

        verifyResponse(lockCandidate(), RPC_REPLY_OK);

        try {
            lock();
            fail("Should have failed - locking of running datastore is not supported");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.application);
        }


        try {
            lockWithoutTarget();
            fail("Should have failed, target is missing");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.invalid_value);
            assertTrue(e.getErrorType() == ErrorType.application);
        }
    }

    @Test
    public void testUnlock() throws Exception {

        verifyResponse(unlockCandidate(), RPC_REPLY_OK);

        try {
            unlock();
            fail("Should have failed - unlocking of running datastore is not supported");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.application);
        }

        try {
            unlockWithoutTarget();
            fail("Should have failed, target is missing");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.invalid_value);
            assertTrue(e.getErrorType() == ErrorType.application);
        }
    }

    @Test
    public void testEditWithCreate() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_create.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfig_create_n1_control.xml"));


        try {
            edit("messages/mapping/editConfigs/editConfig_create.xml");
            fail("Create should have failed - data already exists");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.data_exists);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }

        verifyResponse(discardChanges(), RPC_REPLY_OK);

    }

    @Test
    public void testEditFromFile() throws Exception {
        verifyResponse(editIncludeURL("messages/mapping/files/editConfig_read_only.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfig_create_n1_control.xml"));

        deleteDatastore();

        verifyResponse(editIncludeURL("messages/mapping/files/editConfig_read_write.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfig_create_n1_control.xml"));
    }

    @Test
    public void testEditFromFileIncorrect() throws Exception {
        try {
            editIncludeURL("messages/mapping/files/editConfig_blacklist.xml");
            fail("Edit-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            editIncludeURL("messages/mapping/files/editConfig_file_not_exist.xml");
            fail("Edit-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            editIncludeURL("messages/mapping/files/editConfig_url_empty.xml");
            fail("Edit-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    @Test
    public void testDeleteNonExisting() throws Exception {

        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());

        try {
            edit("messages/mapping/editConfigs/editConfig_delete-top.xml");
            fail("Delete should have failed - data is missing");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.data_missing);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }

    }

    @Test
    public void testEditMissingDefaultOperation() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_1.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_2.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_control.xml"));

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_control.xml"));

        deleteDatastore();
    }

    @Test
    public void testEditConfigWithMultipleOperations() throws Exception {
        deleteDatastore();

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_setup.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_1.xml"), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_2.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_2_control.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_3_leaf_operations.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_3_control.xml"));

        deleteDatastore();

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_setup.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_default-replace.xml"), RPC_REPLY_OK);

        try {
            edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_create_existing.xml");
            fail();
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.data_exists);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete_children_operations.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete_children_operations_control.xml"));
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_remove-non-existing.xml"), RPC_REPLY_OK);

        try {
            edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete-non-existing.xml");
            fail();
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.data_missing);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup-control.xml"));

        // Test files have been modified. RFC6020 requires that at most once case inside a choice is present at any time
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup2.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup2-control.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_delete.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete_children_operations_control.xml"));

        deleteDatastore();
    }

    @Test
    public void testFiltering() throws Exception {

        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-config-empty-filter.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));
        verifyResponse(getWithFilter("messages/mapping/filters/get-empty-filter.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));

        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));
        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-users.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyFilterIdentifier("messages/mapping/filters/get-filter-alluser.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-company-info.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-modules-and-admin.xml",
                YangInstanceIdentifier.builder().node(TOP).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-only-names-types.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-specific-module-type-and-user.xml",
                YangInstanceIdentifier.builder().node(TOP).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-superuser.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-users.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).build());

        YangInstanceIdentifier ident = YangInstanceIdentifier.
                builder(AUGMENTED_CONTAINER_IN_MODULES).
                node(AUGMENTED_CONTAINER).
                node(AUGMENTED_STRING_IN_CONT).build();

        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-string.xml", ident);
        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(AUGMENTED_CASE).build());

        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case.xml"));

        /*
         *  RFC6020 requires that at most once case inside a choice is present at any time.
         *  Therefore
         *  <augmented-case>augmented case</augmented-case>
         *  from
         *  messages/mapping/editConfigs/editConfig-filtering-setup.xml
         *  cannot exists together with
         *  <text>augmented nested choice text1</text>
         *  from
         *  messages/mapping/editConfigs/editConfig-filtering-setup2.xml
         */
        //verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup2.xml"), RPC_REPLY_OK);
        //verifyResponse(commit(), RPC_REPLY_OK);

        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case-inner-choice.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(CHOICE_WRAPPER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case-inner-case.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(CHOICE_WRAPPER).node(INNER_CHOICE).node(INNER_CHOICE_TEXT).build());

//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-string.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-string.xml"));
//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case-inner-choice.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case-inner-choice.xml"));
//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case-inner-case.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case-inner-choice.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_delete-top.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

    }

    @Test
    public void testDeleteConfigCandidate() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        verifyResponse(deleteConfig("messages/mapping/delete_config_candidate.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testDeleteConfigRunning() throws Exception {
        try {
            deleteConfig("messages/mapping/delete_config_running.xml");
            fail("Delete config should have failed");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.operation_not_supported);
            assertTrue(e.getErrorType() == ErrorType.protocol);
        }
    }

    @Test
    public void testDeleteConfigFromFile() throws Exception {
        File temporalyFile = new File(createPathFromClassLoader("messages/mapping/files/read-write/config_element.xml")+"/config_element_for_delete.xml");
        temporalyFile.createNewFile();
        verifyResponse(deleteConfigIncludeURL("messages/mapping/files/deleteConfig_read_write.xml"), RPC_REPLY_OK);
        assertFalse(temporalyFile.exists());
    }

    @Test
    public void testDeleteConfigFromFileIncorrect() throws Exception {
        try {
            deleteConfigIncludeURL("messages/mapping/files/deleteConfig_read_only.xml");
            fail("Delete-config-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            deleteConfigIncludeURL("messages/mapping/files/deleteConfig_blacklist.xml");
            fail("Delete-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            deleteConfigIncludeURL("messages/mapping/files/deleteConfig_file_not_exist.xml");
            fail("Delete-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }

        try {
            deleteConfigIncludeURL("messages/mapping/files/deleteConfig_url_empty.xml");
            fail("Delete-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    @Test
    public void testCopyConfigCandidateToRunning() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigRunning());
        verifyResponse(copyConfig("messages/mapping/copy_config_candidate_to_running.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));
    }

    @Test
    public void testCopyConfigRunningToCandidate() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(copyConfig("messages/mapping/copy_config_running_to_candidate.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigRunning());
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testCopyConfigURLToCandidate() throws Exception {
        verifyResponse(copyConfigIncludeURL("messages/mapping/files/copyConfig_url_to_candidate.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfig_create_n1_control.xml"));
    }

    @Test
    public void testCopyConfigURLToRunning() throws Exception {
        verifyResponse(copyConfigIncludeURL("messages/mapping/files/copyConfig_url_to_running.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/editConfig_create_n1_control.xml"));
    }

    @Test
    public void testCopyConfigCandidateToUrl() throws Exception {
        // prepare candidate
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_create.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup.xml"), RPC_REPLY_OK);
        //store candidate to file
        verifyResponse(copyConfigIncludeURL("messages/mapping/files/copyConfig_candidate_to_url.xml"), RPC_REPLY_OK);
        // read stored file to document
        String basePath = createPathFromClassLoader("messages/mapping/files/copyConfig_candidate_to_url.xml");
        Document storedDocument = XmlUtil.readXmlToDocument(new FileInputStream(new File(basePath+"/read-write/candidate.xml")));

        //compare expectations with stored document
        verifyResponse(storedDocument, XmlFileLoader.xmlFileToDocument("messages/mapping/files/candidate_control.xml"));

        deleteDatastore();
        // call edit-config on the stored document. And store it to candidate. Then verify
        verifyResponse(editIncludeURL("messages/mapping/files/copyConfig_verified_candidate.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/files/candidate_rpc_control.xml"));
    }

    @Test
    public void testCopyConfigRunningToUrl() throws Exception {
        // prepare running
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_create.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        //store running to file
        verifyResponse(copyConfigIncludeURL("messages/mapping/files/copyConfig_running_to_url.xml"), RPC_REPLY_OK);
        // read stored file to document
        String basePath = createPathFromClassLoader("messages/mapping/files/copyConfig_running_to_url.xml");
        Document storedDocument = XmlUtil.readXmlToDocument(new FileInputStream(new File(basePath+"/read-write/running.xml")));

        //compare expectations with stored document
        verifyResponse(storedDocument, XmlFileLoader.xmlFileToDocument("messages/mapping/files/running_control.xml"));
    }

    @Test
    public void testCopyConfigEmptyCandidateToUrl() throws Exception {
        //store candidate to file
        verifyResponse(copyConfigIncludeURL("messages/mapping/files/copyConfig_candidate_to_url.xml"), RPC_REPLY_OK);

        // read stored file to document
        String basePath = createPathFromClassLoader("messages/mapping/files/copyConfig_candidate_to_url.xml");
        Document storedDocument = XmlUtil.readXmlToDocument(new FileInputStream(new File(basePath+"/read-write/candidate.xml")));

        //compare expectations with stored document
        verifyResponse(storedDocument, XmlFileLoader.xmlFileToDocument("messages/mapping/files/candidate_empty_control.xml"));
    }

    @Test
    public void testCopyConfigIncorrect() throws Exception {
        try {
            validateIncludeURL("messages/mapping/files/copyConfig_blacklist.xml");
            fail("Copy-config should have failed");
        } catch (DocumentedException e) {
            assertOperationFailed(e);
        }
    }

    @Test
    public void testCopyConfigIncorrate() throws Exception {
        copyConfigTest("messages/mapping/copy_config_source_missing.xml");
        copyConfigTest("messages/mapping/copy_config_target_missing.xml");
    }

    private void copyConfigTest(String filePath) throws Exception {
        try {
            copyConfig(filePath);
            fail("Copy config should have failed");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.error);
            assertTrue(e.getErrorTag() == ErrorTag.invalid_value);
            assertTrue(e.getErrorType() == ErrorType.application);
        }
    }

    private void verifyFilterIdentifier(String resource, YangInstanceIdentifier identifier) throws Exception{
        TestingGetConfig getConfig = new TestingGetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider);
        Document request = XmlFileLoader.xmlFileToDocument(resource);
        YangInstanceIdentifier iid = getConfig.getInstanceIdentifierFromDocument(request);
        assertEquals(identifier, iid);
    }

    private class TestingGetConfig extends GetConfig{
        public TestingGetConfig(String sessionId, CurrentSchemaContext schemaContext, TransactionProvider transactionProvider) {
            super(sessionId, schemaContext, transactionProvider);
        }

        public YangInstanceIdentifier getInstanceIdentifierFromDocument(Document request) throws DocumentedException {
            XmlElement filterElement = XmlElement.fromDomDocument(request).getOnlyChildElement(GET_CONFIG).getOnlyChildElement(FILTER_NODE);
            return getInstanceIdentifierFromFilter(filterElement);
        }
    }

    private void deleteDatastore() throws Exception{
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_delete-root.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());

        verifyResponse(commit(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigRunning());
    }

    private void assertEmptyDatastore(Document response) {

        NodeList nodes = response.getChildNodes();
        assertTrue(nodes.getLength() == 1);

        assertEquals(nodes.item(0).getLocalName(), RPC_REPLY_ELEMENT);

        NodeList replyNodes = nodes.item(0).getChildNodes();
        assertTrue(replyNodes.getLength() == 1);

        Node dataNode = replyNodes.item(0);
        assertEquals(dataNode.getLocalName(), DATA_ELEMENT);
        assertFalse(dataNode.hasChildNodes());

    }

    private Document commit() throws Exception {
        Commit commit = new Commit(sessionIdForReporting, transactionProvider);
        return executeOperation(commit, "messages/mapping/commit.xml");
    }

    private Document discardChanges() throws Exception {
        DiscardChanges discardOp = new DiscardChanges(sessionIdForReporting, transactionProvider);
        return executeOperation(discardOp, "messages/mapping/discardChanges.xml");
    }

    private Document editIncludeURL(String resource) throws Exception {
        EditConfig editConfig = new EditConfig(sessionIdForReporting, currentSchemaContext, transactionProvider, netconfFileService);
        return executeOperation(editConfig, resource, true);
    }

    private Document edit(String resource) throws Exception {
        EditConfig editConfig = new EditConfig(sessionIdForReporting, currentSchemaContext, transactionProvider, netconfFileService);
        return executeOperation(editConfig, resource);
    }

    private Document get() throws Exception {
        Get get = new Get(sessionIdForReporting, currentSchemaContext, transactionProvider);
        return executeOperation(get, "messages/mapping/get.xml");
    }

    private Document getWithFilter(String resource) throws Exception {
        Get get = new Get(sessionIdForReporting, currentSchemaContext, transactionProvider);
        return executeOperation(get, resource);
    }

    private Document getConfigRunning() throws Exception {
        GetConfig getConfig = new GetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig.xml");
    }

    private Document getConfigCandidate() throws Exception {
        GetConfig getConfig = new GetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig_candidate.xml");
    }

    private Document getConfigWithFilter(String resource) throws Exception {
        GetConfig getConfig = new GetConfig(sessionIdForReporting, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, resource);
    }

    private Document lock() throws Exception {
        Lock lock = new Lock(sessionIdForReporting);
        return executeOperation(lock, "messages/mapping/lock.xml");
    }

    private Document unlock() throws Exception {
        Unlock unlock = new Unlock(sessionIdForReporting);
        return executeOperation(unlock, "messages/mapping/unlock.xml");
    }

    private Document lockWithoutTarget() throws Exception {
        Lock lock = new Lock(sessionIdForReporting);
        return executeOperation(lock, "messages/mapping/lock_notarget.xml");
    }

    private Document unlockWithoutTarget() throws Exception {
        Unlock unlock = new Unlock(sessionIdForReporting);
        return executeOperation(unlock, "messages/mapping/unlock_notarget.xml");
    }

    private Document lockCandidate() throws Exception {
        Lock lock = new Lock(sessionIdForReporting);
        return executeOperation(lock, "messages/mapping/lock_candidate.xml");
    }

    private Document unlockCandidate() throws Exception {
        Unlock unlock = new Unlock(sessionIdForReporting);
        return executeOperation(unlock, "messages/mapping/unlock_candidate.xml");
    }

    private Document validate(String resource) throws Exception {
        Validate validate = new Validate(sessionIdForReporting, currentSchemaContext, netconfFileService);
        return executeOperation(validate, resource);
    }

    private Document validateIncludeURL(String resource) throws Exception {
        Validate validate = new Validate(sessionIdForReporting, currentSchemaContext, netconfFileService);
        return executeOperation(validate, resource, true);
    }

    private Document deleteConfig(String resource) throws Exception {
        DeleteConfig deleteConfig = new DeleteConfig(sessionIdForReporting, transactionProvider, netconfFileService);
        return executeOperation(deleteConfig, resource);
    }

    private Document deleteConfigIncludeURL(String resource) throws Exception {
        DeleteConfig deleteConfig = new DeleteConfig(sessionIdForReporting, transactionProvider, netconfFileService);
        return executeOperation(deleteConfig, resource, true);
    }

    private Document copyConfig(String resource) throws Exception {
        CopyConfig copyconfig = new CopyConfig(sessionIdForReporting, currentSchemaContext, transactionProvider, netconfFileService);
        return executeOperation(copyconfig, resource);
    }

    private Document copyConfigIncludeURL(String resource) throws Exception {
        CopyConfig copyconfig = new CopyConfig(sessionIdForReporting, currentSchemaContext, transactionProvider, netconfFileService);
        return executeOperation(copyconfig, resource, true);
    }

    private Document executeOperation(NetconfOperation op, String filename) throws Exception {
        return executeOperation(op,filename,false);
    }

    private Document executeOperation(NetconfOperation op, String filename, boolean includeURL) throws Exception {
        Document request = XmlFileLoader.xmlFileToDocument(filename);
        if (includeURL) {
            String requestString  = printDocument(request).replace("${path}",createPathFromClassLoader(filename));
            request = XmlUtil.readXmlToDocument(requestString);
        }

        final Document response = op.handle(request, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);


        LOG.debug("Got response {}" , response);
        return response;
    }

    private String createPathFromClassLoader(String relativePath) {
        URL baseURL = XmlFileLoader.class.getClassLoader().getResource(relativePath);
        return baseURL.getPath().substring(0,baseURL.getPath().lastIndexOf("/"));
    }

    private List<InputStream> getYangSchemas() {
        final List<String> schemaPaths = Arrays.asList("/META-INF/yang/config.yang", "/yang/mdsal-netconf-mapping-test.yang");
        final List<InputStream> schemas = new ArrayList<>();

        for (String schemaPath : schemaPaths) {
            InputStream resourceAsStream = getClass().getResourceAsStream(schemaPath);
            schemas.add(resourceAsStream);
        }

        return schemas;
    }

    private static SchemaContext parseYangStreams(final List<InputStream> streams) {
        CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        final SchemaContext schemaContext;
        try {
            schemaContext = reactor.buildEffective(streams);
        } catch (ReactorException e) {
            throw new RuntimeException("Unable to build schema context from " + streams, e);
        }
        return schemaContext;
    }

    private SchemaService createSchemaService() {
        return new SchemaService() {

            @Override
            public void addModule(Module module) {
            }

            @Override
            public void removeModule(Module module) {

            }

            @Override
            public SchemaContext getSessionContext() {
                return schemaContext;
            }

            @Override
            public SchemaContext getGlobalContext() {
                return schemaContext;
            }

            @Override
            public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(final SchemaContextListener listener) {
                listener.onGlobalContextUpdated(getGlobalContext());
                return new ListenerRegistration<SchemaContextListener>() {
                    @Override
                    public void close() {

                    }

                    @Override
                    public SchemaContextListener getInstance() {
                        return listener;
                    }
                };
            }
        };
    }
}
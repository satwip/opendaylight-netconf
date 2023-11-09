/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveKeystoreEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveKeystoreEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveKeystoreEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemovePrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemovePrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemovePrivateKeyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemovePrivateKeyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.RemoveTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.keystore.entry.KeyCredentialBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.keystore.entry.KeyCredentialKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.rpc._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.rpc.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.Rpc;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = { })
public final class NetconfSalKeystoreRpcs implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSalKeystoreRpcs.class);
    private static final InstanceIdentifier<Keystore> KEYSTORE_IID = InstanceIdentifier.create(Keystore.class);

    // FIXME: we are populating config datastore, but there may be risks with concurrent access. We really should be
    //        using cluster singleton service here.
    private final DataBroker dataBroker;
    private final AAAEncryptionService encryptionService;
    private final Registration reg;

    @Inject
    @Activate
    public NetconfSalKeystoreRpcs(@Reference final DataBroker dataBroker,
            @Reference final AAAEncryptionService encryptionService, @Reference final RpcProviderService rpcProvider) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);

        reg = rpcProvider.registerRpcImplementations(ImmutableClassToInstanceMap.<Rpc<?, ?>>builder()
            .put(RemoveKeystoreEntry.class, this::removeKeystoreEntry)
            .put(AddKeystoreEntry.class, this::addKeystoreEntry)
            .put(AddTrustedCertificate.class, this::addTrustedCertificate)
            .put(RemoveTrustedCertificate.class, this::removeTrustedCertificate)
            .put(AddPrivateKey.class, this::addPrivateKey)
            .put(RemovePrivateKey.class, this::removePrivateKey)
            .build());
        LOG.info("NETCONF keystore service started");
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        LOG.info("NETCONF keystore service stopped");
    }

    private ListenableFuture<RpcResult<RemoveKeystoreEntryOutput>> removeKeystoreEntry(
            final RemoveKeystoreEntryInput input) {
        LOG.debug("Removing keypairs: {}", input);

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        for (final String id : input.getKeyId()) {
            writeTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(KeyCredential.class, new KeyCredentialKey(id)));
        }

        final SettableFuture<RpcResult<RemoveKeystoreEntryOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("remove-key-pair success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new RemoveKeystoreEntryOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("remove-key-pair failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }

    private ListenableFuture<RpcResult<AddKeystoreEntryOutput>> addKeystoreEntry(final AddKeystoreEntryInput input) {
        LOG.debug("Adding keypairs: {}", input);

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final List<KeyCredential> keypairs = input.nonnullKeyCredential().values().stream()
            .map(keypair -> new KeyCredentialBuilder()
                .setKeyId(keypair.getKeyId())
                .setPrivateKey(encryptionService.encrypt(keypair.getPrivateKey().getBytes(StandardCharsets.US_ASCII)))
                .setPassphrase(encryptionService.encrypt(keypair.getPassphrase()))
                .build())
            .collect(Collectors.toList());

        for (KeyCredential keypair : keypairs) {
            writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(KeyCredential.class, keypair.key()), keypair);
        }

        final SettableFuture<RpcResult<AddKeystoreEntryOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("add-key-pair success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new AddKeystoreEntryOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("add-key-pair failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }

    @VisibleForTesting
    ListenableFuture<RpcResult<AddTrustedCertificateOutput>> addTrustedCertificate(
            final AddTrustedCertificateInput input) {
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        for (TrustedCertificate certificate : input.nonnullTrustedCertificate().values()) {
            final var base64certificate = new TrustedCertificateBuilder()
                .setName(certificate.getName())
                .setCertificate(certificate.getCertificate().getBytes(StandardCharsets.US_ASCII))
                .build();

            writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109
                    .trusted.certificates.TrustedCertificate.class,
                base64certificate.key()), base64certificate);
        }

        final SettableFuture<RpcResult<AddTrustedCertificateOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("add-trusted-certificate success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("add-trusted-certificate failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }

    private ListenableFuture<RpcResult<RemoveTrustedCertificateOutput>> removeTrustedCertificate(
            final RemoveTrustedCertificateInput input) {
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        for (final String name : input.getName()) {
            writeTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109
                    .trusted.certificates.TrustedCertificate.class,
                    new TrustedCertificateKey(name)));
        }

        final SettableFuture<RpcResult<RemoveTrustedCertificateOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("remove-trusted-certificate success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new RemoveTrustedCertificateOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("remove-trusted-certificate failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }

    @VisibleForTesting
    ListenableFuture<RpcResult<AddPrivateKeyOutput>> addPrivateKey(final AddPrivateKeyInput input) {
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        for (PrivateKey key: input.nonnullPrivateKey().values()) {
            final var base64key = new PrivateKeyBuilder()
                .setName(key.getName())
                .setData(key.getData().getBytes(StandardCharsets.US_ASCII))
                .setCertificateChain(key.getCertificateChain().stream()
                    .map(cert -> cert.getBytes(StandardCharsets.US_ASCII)).toList())
                .build();

            writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109
                    ._private.keys.PrivateKey.class,
                base64key.key()), base64key);
        }

        final SettableFuture<RpcResult<AddPrivateKeyOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("add-private-key success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new AddPrivateKeyOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("add-private-key failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }

    private ListenableFuture<RpcResult<RemovePrivateKeyOutput>> removePrivateKey(final RemovePrivateKeyInput input) {
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        for (final String name : input.getName()) {
            writeTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                KEYSTORE_IID.child(org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109
                    ._private.keys.PrivateKey.class,
                    new PrivateKeyKey(name)));
        }

        final SettableFuture<RpcResult<RemovePrivateKeyOutput>> rpcResult = SettableFuture.create();

        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("remove-private-key success. Input: {}", input);
                rpcResult.set(RpcResultBuilder.success(new RemovePrivateKeyOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("remove-private-key failed. Input: {}", input, throwable);
                rpcResult.setException(throwable);
            }
        }, MoreExecutors.directExecutor());

        return rpcResult;
    }
}

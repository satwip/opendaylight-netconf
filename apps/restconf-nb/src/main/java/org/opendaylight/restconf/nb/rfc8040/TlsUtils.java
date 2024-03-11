/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.DecoderException;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TlsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TlsUtils.class);

    record CertificateKey(X509Certificate certificate, PrivateKey privateKey) {
    }

    private TlsUtils() {
        // hidden on purpose
    }

    static @Nullable CertificateKey readCertificateKey(final String certificateFilePath,
        final String privateKeyFilePath) {
        if (certificateFilePath.isEmpty() || privateKeyFilePath.isEmpty()) {
            return null;
        }
        final var certificate = readCertificate(new File(certificateFilePath));
        final var privateKey = readPrivateKey(new File(privateKeyFilePath));
        return certificate == null || privateKey == null ? null : new CertificateKey(certificate, privateKey);
    }

    private static X509Certificate readCertificate(File file) {
        if (!file.exists()) {
            LOG.warn("Configured certificate file {} file does not exists", file.getAbsolutePath());
            return null;
        }
        try (var certReader = new PEMParser(new FileReader(file, StandardCharsets.UTF_8))) {
            final var obj = certReader.readObject();
            if (obj instanceof X509CertificateHolder cert) {
                return new JcaX509CertificateConverter().getCertificate(cert);
            } else {
                LOG.warn("Configured certificate file {} contains unexpected object {}",
                    file.getAbsolutePath(), obj.getClass());
            }
        } catch (IOException | DecoderException | CertificateException e) {
            LOG.warn("Error reading certificate file {} ", file.getAbsolutePath(), e);
        }
        return null;
    }

    private static PrivateKey readPrivateKey(final File file) {
        if (!file.exists()) {
            LOG.warn("Configured private key file {} file does not exists", file.getAbsolutePath());
            return null;
        }
        try (var keyReader = new PEMParser(new FileReader(file, StandardCharsets.UTF_8))) {
            final var obj = keyReader.readObject();
            if (obj instanceof PEMKeyPair keyPair) {
                return new JcaPEMKeyConverter().getKeyPair(keyPair).getPrivate();
            } else if (obj instanceof PrivateKeyInfo pkInfo) {
                return new JcaPEMKeyConverter().getPrivateKey(pkInfo);
            } else {
                LOG.warn("Configured private key file {} contains unexpected object {}",
                    file.getAbsolutePath(), obj.getClass());
            }
        } catch (IOException | DecoderException e) {
            LOG.warn("Error reading private key file {} ", file.getAbsolutePath(), e);
        }
        return null;
    }
}

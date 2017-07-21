/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.ssh.authentication;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class Providing username/password authentication option to
 * {@link org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandler}
 */
public class LoginPassword extends AuthenticationHandler {

    private final String username;
    private final String password;
    private final AAAEncryptionService encryptionService;
    private final Logger LOG = LoggerFactory.getLogger(LoginPassword.class);

    public LoginPassword(String username, String password) {
        this(username, password, null);
    }

    public LoginPassword(final String username, final String password, final AAAEncryptionService encryptionService) {
        this.username = username;
        this.password = password;
        this.encryptionService = encryptionService;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public AuthFuture authenticate(final ClientSession session) throws IOException {
        if (encryptionService != null) {
            String decryptedPassword = password;
            try {
                decryptedPassword = encryptionService.decrypt(password);
            } catch (GeneralSecurityException e) {
                LOG.warn("Unable to decrypt password.");
            }
            session.addPasswordIdentity(decryptedPassword);
        } else {
            session.addPasswordIdentity(password);
        }
        return session.auth();
    }
}

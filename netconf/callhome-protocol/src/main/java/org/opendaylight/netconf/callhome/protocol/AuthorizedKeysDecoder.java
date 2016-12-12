/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

import org.apache.sshd.common.util.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizedKeysDecoder {

    private static final String KEY_FACTORY_TYPE_RSA = "RSA";
    private static final String KEY_FACTORY_TYPE_DSA = "DSA";
    private static final String KEY_FACTORY_TYPE_ECDSA = "EC";

    private static final String KEY_TYPE_RSA = "ssh-rsa";
    private static final String KEY_TYPE_DSA = "ssh-dss";
    private static final String KEY_TYPE_ECDSA = "ecdsa-sha2-nistp256";

    private static Logger log = LoggerFactory.getLogger(AuthorizedKeysDecoder.class);

    private byte[] bytes = new byte[0];
    private int pos = 0;

    public PublicKey decodePublicKey(String keyLine) throws InvalidKeySpecException, NoSuchAlgorithmException {

        // look for the Base64 encoded part of the line to decode
        // both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
        bytes = Base64.decodeBase64(keyLine.getBytes());
        if (bytes.length == 0)
            throw new IllegalArgumentException("no Base64 part to decode");

        String type = decodeType();
        if (type.equals(KEY_TYPE_RSA))
            return decodeAsRSA();

        if (type.equals(KEY_TYPE_DSA))
            return decodeAsDSA();

        if(type.equals(KEY_TYPE_ECDSA))
            return decodeAsECDSA();

        throw new IllegalArgumentException("unknown type " + type);
    }

    private PublicKey decodeAsECDSA() throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        KeyFactory ecdsaFactory = KeyFactory.getInstance(KEY_FACTORY_TYPE_ECDSA);
        ECNamedCurveParameterSpec spec256r1 = ECNamedCurveTable.getParameterSpec("secp256r1");
        ECNamedCurveSpec params256r1 = new ECNamedCurveSpec("secp256r1", spec256r1.getCurve(), spec256r1.getG(), spec256r1.getN());
        // copy last 65 bytes from ssh key.
        ECPoint point =  ECPointUtil.decodePoint(params256r1.getCurve(), Arrays.copyOfRange(bytes,39,bytes.length));
        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, params256r1);
        return ecdsaFactory.generatePublic(pubKeySpec);
    }

    private PublicKey decodeAsDSA() throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        KeyFactory dsaFactory = KeyFactory.getInstance(KEY_FACTORY_TYPE_DSA);
        BigInteger p = decodeBigInt();
        BigInteger q = decodeBigInt();
        BigInteger g = decodeBigInt();
        BigInteger y = decodeBigInt();
        DSAPublicKeySpec spec = new DSAPublicKeySpec(y, p, q, g);
        return dsaFactory.generatePublic(spec);
    }

    private PublicKey decodeAsRSA() throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        KeyFactory rsaFactory = KeyFactory.getInstance(KEY_FACTORY_TYPE_RSA);
        BigInteger exponent = decodeBigInt();
        BigInteger modulus = decodeBigInt();
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return rsaFactory.generatePublic(spec);
    }

    private String decodeType() {
        int len = decodeInt();
        String type = new String(bytes, pos, len);
        pos += len;
        return type;
    }

    private int decodeInt() {
        return ((bytes[pos++] & 0xFF) << 24) | ((bytes[pos++] & 0xFF) << 16)
                | ((bytes[pos++] & 0xFF) << 8) | (bytes[pos++] & 0xFF);
    }

    private BigInteger decodeBigInt() {
        int len = decodeInt();
        byte[] bigIntBytes = new byte[len];
        System.arraycopy(bytes, pos, bigIntBytes, 0, len);
        pos += len;
        return new BigInteger(bigIntBytes);
    }
}

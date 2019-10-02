/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.utils;

import android.util.Log;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;

public class Otp {

    private static final Base32 BASE_32 = new Base32();

    private Otp() {}

    public static String calculateCode(
            String secret, long counter, String algorithm, String digits) {
        String[] steam = {
            "2", "3", "4", "5", "6", "7", "8", "9", "B", "C", "D", "F", "G", "H", "J", "K", "M",
            "N", "P", "Q", "R", "T", "V", "W", "X", "Y"
        };
        String ALGORITHM = "Hmac" + algorithm.toUpperCase();
        SecretKeySpec signingKey = new SecretKeySpec(BASE_32.decode(secret), ALGORITHM);

        Mac mac;
        try {
            mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);
        } catch (NoSuchAlgorithmException e) {
            Log.e("TOTP", ALGORITHM + " unavailable - should never happen", e);
            return null;
        } catch (InvalidKeyException e) {
            Log.e("TOTP", "Key is malformed", e);
            return null;
        }

        byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
        int offset = digest[digest.length - 1] & 0xf;
        byte[] code = Arrays.copyOfRange(digest, offset, offset + 4);
        code[0] = (byte) (0x7f & code[0]);
        String strCode = new BigInteger(code).toString();
        if (digits.equals("s")) {
            StringBuilder output = new StringBuilder();
            int bigInt = new BigInteger(code).intValue();
            for (int i = 0; i != 5; i++) {
                output.append(steam[bigInt % 26]);
                bigInt /= 26;
            }
            return output.toString();
        } else return strCode.substring(strCode.length() - Integer.parseInt(digits));
    }
}

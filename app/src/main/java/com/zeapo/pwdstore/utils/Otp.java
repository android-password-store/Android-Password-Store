package com.zeapo.pwdstore.utils;

import android.util.Log;
import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Otp {
    public static final int TIME_WINDOW = 30;

    private static final String ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;

    private static final Base32 BASE_32 = new Base32();

    private Otp() {
    }

    public static String calculateCode(String secret, long counter) {
        SecretKeySpec signingKey = new SecretKeySpec(BASE_32.decode(secret), ALGORITHM);

        Mac mac = null;
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
        return strCode.substring(strCode.length() - CODE_DIGITS);
    }
}

package com.zeapo.pwdstore.pwgen;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class randnum {
    private static SecureRandom random;

    static {
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("SHA1PRNG not available", e);
        }
    }

    /**
     * Generate a random number n, where 0 &lt;= n &lt; maxNum.
     *
     * @param maxNum the bound on the random number to be returned
     * @return the generated random number
     */
    public static int number(int maxNum) {
        return random.nextInt(maxNum);
    }
}

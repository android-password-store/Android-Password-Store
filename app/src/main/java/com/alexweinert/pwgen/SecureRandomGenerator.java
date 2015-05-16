package com.alexweinert.pwgen;

import java.security.SecureRandom;

/** Returns a random number based on Android's SecureRandom-class */
public class SecureRandomGenerator implements IRandom {

    SecureRandom secureRandomGenerator;

    public SecureRandomGenerator() {
        this.secureRandomGenerator = new SecureRandom();
    }

    @Override
    public int getRandomInt(int max) {
        return this.secureRandomGenerator.nextInt(max);
    }

}

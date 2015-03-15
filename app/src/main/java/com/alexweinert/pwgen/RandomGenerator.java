package com.alexweinert.pwgen;

import java.util.Random;

/** Returns a random number based on Android's Random-class */
public class RandomGenerator implements IRandom {

    Random randomGenerator;

    public RandomGenerator() {
        this.randomGenerator = new Random();
    }

    @Override
    public int getRandomInt(int max) {
        return this.randomGenerator.nextInt(max);
    }

}

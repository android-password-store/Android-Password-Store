package com.alexweinert.pwgen;

public interface IRandom {
    /** Returns a random value from the interval [0,max) */
    int getRandomInt(int max);
}

/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgen

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

internal object RandomNumberGenerator {
    private var random: SecureRandom

    init {
        try {
            random = SecureRandom.getInstance("SHA1PRNG")
        } catch (e: NoSuchAlgorithmException) {
            throw SecurityException("SHA1PRNG not available", e)
        }
    }

    /**
     * Generate a random number n, where 0 &lt;= n &lt; maxNum.
     *
     * @param maxNum the bound on the random number to be returned
     * @return the generated random number
     */
    fun number(maxNum: Int): Int {
        return random.nextInt(maxNum)
    }
}

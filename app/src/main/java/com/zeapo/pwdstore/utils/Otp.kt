/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import com.github.ajalt.timberkt.e
import org.apache.commons.codec.binary.Base32
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

object Otp {
    private val BASE_32 = Base32()

    fun calculateCode(secret: String, counter: Long, algorithm: String, digits: String): String? {
        val steam = arrayOf("2", "3", "4", "5", "6", "7", "8", "9", "B", "C", "D", "F", "G", "H", "J", "K", "M",
            "N", "P", "Q", "R", "T", "V", "W", "X", "Y")
        val algo = "Hmac${algorithm.toUpperCase(Locale.ROOT)}"
        val signingKey = SecretKeySpec(BASE_32.decode(secret), algo)
        val mac: Mac
        try {
            mac = Mac.getInstance(algo)
            mac.init(signingKey)
        } catch (e: NoSuchAlgorithmException) {
            e(e)
            return null
        } catch (e: InvalidKeyException) {
            e(e) { "Key is malformed" }
            return null
        }
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = (digest[digest.size - 1] and 0xf).toInt()
        val code = Arrays.copyOfRange(digest, offset, offset + 4)
        code[0] = (0x7f and code[0].toInt()).toByte()
        val strCode = BigInteger(code).toString()
        return if (digits == "s") {
            val output = StringBuilder()
            var bigInt = BigInteger(code).toInt()
            repeat(5) {
                output.append(steam[bigInt % 26])
                bigInt /= 26
            }
            output.toString()
        } else strCode.substring(strCode.length - digits.toInt())
    }
}

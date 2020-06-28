/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import com.github.ajalt.timberkt.e
import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

object Otp {
    private val BASE_32 = Base32()
    private val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY".toCharArray()
    init {
        check(STEAM_ALPHABET.size == 26)
    }

    fun calculateCode(secret: String, counter: Long, algorithm: String, digits: String): String? {
        val algo = "Hmac${algorithm.toUpperCase(Locale.ROOT)}"
        val secretKey = SecretKeySpec(BASE_32.decode(secret), algo)
        val digest = try {
            Mac.getInstance(algo).run {
                init(secretKey)
                doFinal(ByteBuffer.allocate(8).putLong(counter).array())
            }
        } catch (e: NoSuchAlgorithmException) {
            e(e)
            return null
        } catch (e: InvalidKeyException) {
            e(e) { "Key is malformed" }
            return null
        }
        // Least significant 4 bits are used as an offset into the digest.
        val offset = (digest.last() and 0xf).toInt()
        // Extract 32 bits at the offset and clear the most significant bit.
        val code = digest.copyOfRange(offset, offset + 4)
        code[0] = (0x7f and code[0].toInt()).toByte()
        val codeInt = ByteBuffer.wrap(code).int
        check(codeInt > 0)
        return if (digits == "s") {
            // Steam
            var remainingCodeInt = codeInt
            buildString {
                repeat(5) {
                    append(STEAM_ALPHABET[remainingCodeInt % 26])
                    remainingCodeInt /= 26
                }
            }
        } else {
            // Base 10, 6 to 10 digits
            val numDigits = digits.toIntOrNull()
            when {
                numDigits == null -> {
                    e { "Digits specifier has to be either 's' or numeric" }
                    return null
                }
                numDigits < 6 -> {
                    e { "TOTP codes have to be at least 6 digits long" }
                    return null
                }
                numDigits > 10 -> {
                    e { "TOTP codes can be at most 10 digits long" }
                    return null
                }
                else -> {
                    // 2^31 = 2_147_483_648, so we can extract at most 10 digits with the first one
                    // always being 0, 1, or 2. Pad with leading zeroes.
                    val codeStringBase10 = codeInt.toString(10).padStart(10, '0')
                    check(codeStringBase10.length == 10)
                    codeStringBase10.takeLast(numDigits)
                }
            }
        }
    }
}

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.totp

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import org.apache.commons.codec.binary.Base32

public object Otp {

  private val BASE_32 = Base32()
  private val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY".toCharArray()
  private const val BYTE_BUFFER_CAPACITY = 8
  private const val END_INDEX_OFFSET = 4
  private const val STEAM_GUARD_DIGITS = 5
  private const val MINIMUM_DIGITS = 6
  private const val MAXIMUM_DIGITS = 10
  private const val ALPHABET_LENGTH = 26
  private const val MOST_SIGNIFICANT_BYTE = 0x7f

  public fun calculateCode(
    secret: String,
    counter: Long,
    algorithm: String,
    digits: String,
    issuer: String?,
  ): Result<String, Throwable> = runCatching {
    val algo = "Hmac${algorithm.uppercase(Locale.ROOT)}"
    val decodedSecret = BASE_32.decode(secret)
    val secretKey = SecretKeySpec(decodedSecret, algo)
    val digest =
      Mac.getInstance(algo).run {
        init(secretKey)
        doFinal(ByteBuffer.allocate(BYTE_BUFFER_CAPACITY).putLong(counter).array())
      }
    // Least significant 4 bits are used as an offset into the digest.
    val offset = (digest.last() and 0xf).toInt()
    // Extract 32 bits at the offset and clear the most significant bit.
    val code = digest.copyOfRange(offset, offset.plus(END_INDEX_OFFSET))
    code[0] = (MOST_SIGNIFICANT_BYTE and code[0].toInt()).toByte()
    val codeInt = ByteBuffer.wrap(code).int
    check(codeInt > 0)
    // SteamGuard is a horrible OTP implementation that generates non-standard 5 digit OTPs as
    // well
    // as uses a custom character set.
    if (digits == "s" || issuer == "Steam") {
      var remainingCodeInt = codeInt
      buildString {
        repeat(STEAM_GUARD_DIGITS) {
          append(STEAM_ALPHABET[remainingCodeInt % STEAM_ALPHABET.size])
          remainingCodeInt /= ALPHABET_LENGTH
        }
      }
    } else {
      // Base 10, 6 to 10 digits
      val numDigits = digits.toIntOrNull()
      when {
        numDigits == null -> {
          return Err(IllegalArgumentException("Digits specifier has to be either 's' or numeric"))
        }
        numDigits < MINIMUM_DIGITS -> {
          return Err(
            IllegalArgumentException("TOTP codes have to be at least $MINIMUM_DIGITS digits long")
          )
        }
        numDigits > MAXIMUM_DIGITS -> {
          return Err(
            IllegalArgumentException("TOTP codes can be at most $MAXIMUM_DIGITS digits long")
          )
        }
        else -> {
          // 2^31 = 2_147_483_648, so we can extract at most 10 digits with the first one
          // always being 0, 1, or 2. Pad with leading zeroes.
          val codeStringBase10 = codeInt.toString(MAXIMUM_DIGITS).padStart(MAXIMUM_DIGITS, '0')
          check(codeStringBase10.length == MAXIMUM_DIGITS)
          codeStringBase10.takeLast(numDigits)
        }
      }
    }
  }
}

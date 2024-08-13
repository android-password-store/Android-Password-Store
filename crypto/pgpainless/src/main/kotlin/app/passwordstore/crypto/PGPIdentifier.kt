/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import java.util.Locale
import java.util.regex.Pattern

/** Supertype for valid identifiers of PGP keys. */
public sealed class PGPIdentifier {

  /** A [PGPIdentifier] that represents either a long key ID or a fingerprint. */
  public data class KeyId(val id: Long) : PGPIdentifier() {
    override fun toString(): String {
      return convertKeyIdToHex(id)
    }

    /** Convert a [Long] key ID to a formatted string. */
    private fun convertKeyIdToHex(keyId: Long): String {
      return convertKeyIdToHex32bit(keyId shr HEX_32_BIT_COUNT) + convertKeyIdToHex32bit(keyId)
    }

    /**
     * Converts [keyId] to an unsigned [Long] then uses [java.lang.Long.toHexString] to convert it
     * to a lowercase hex ID.
     */
    private fun convertKeyIdToHex32bit(keyId: Long): String {
      var hexString = java.lang.Long.toHexString(keyId and HEX_32_BITMASK).lowercase(Locale.ENGLISH)
      while (hexString.length < HEX_32_STRING_LENGTH) {
        hexString = "0$hexString"
      }
      return hexString
    }
  }

  /**
   * A [PGPIdentifier] that represents the textual name/email combination corresponding to a key.
   * Despite the [email] property in this class, the value is not guaranteed to be a valid email.
   */
  public data class UserId(val email: String) : PGPIdentifier() {
    override fun toString(): String {
      return email
    }
  }

  public companion object {
    private const val HEX_RADIX = 16
    private const val HEX_32_BIT_COUNT = 32
    private const val HEX_32_BITMASK = 0xffffffffL
    private const val HEX_32_STRING_LENGTH = 8
    private const val TRUNCATED_FINGERPRINT_LENGTH = 16

    /**
     * Attempts to parse an untyped String identifier into a concrete subtype of [PGPIdentifier].
     */
    @Suppress("ReturnCount")
    public fun fromString(identifier: String): PGPIdentifier? {
      if (identifier.isEmpty()) return null
      // Match long key IDs:
      // FF22334455667788 or 0xFF22334455667788
      val maybeLongKeyId =
        identifier.removePrefix("0x").takeIf { it.matches("[a-fA-F\\d]{16}".toRegex()) }
      if (maybeLongKeyId != null) {
        val keyId = maybeLongKeyId.toULong(HEX_RADIX)
        return KeyId(keyId.toLong())
      }

      // Match fingerprints:
      // FF223344556677889900112233445566778899 or 0xFF223344556677889900112233445566778899
      val maybeFingerprint =
        identifier.removePrefix("0x").takeIf { it.matches("[a-fA-F\\d]{40}".toRegex()) }
      if (maybeFingerprint != null) {
        // Truncating to the long key ID is not a security issue since OpenKeychain only
        // accepts
        // non-ambiguous key IDs.
        val keyId = maybeFingerprint.takeLast(TRUNCATED_FINGERPRINT_LENGTH).toULong(HEX_RADIX)
        return KeyId(keyId.toLong())
      }

      return splitUserId(identifier)?.let { UserId(it) }
    }

    private object UserIdRegex {
      val PATTERN: Pattern = Pattern.compile("^(.*?)(?: \\((.*)\\))?(?: <(.*)>)?$")
      const val NAME = 1
      const val EMAIL = 3
    }

    private object EmailRegex {
      val PATTERN: Pattern = Pattern.compile("^<?\"?([^<>\"]*@[^<>\"]*[.]?[^<>\"]*)\"?>?$")
      const val EMAIL = 1
    }

    /**
     * Takes a 'Name (Comment) <Email>' user ID in any of its permutations and attempts to extract
     * an email from it.
     */
    @Suppress("NestedBlockDepth")
    private fun splitUserId(userId: String): String? {
      if (userId.isNotEmpty()) {
        val matcher = UserIdRegex.PATTERN.matcher(userId)
        if (matcher.matches()) {
          var name =
            if (matcher.group(UserIdRegex.NAME)?.isEmpty() == true) null
            else matcher.group(UserIdRegex.NAME)
          var email = matcher.group(UserIdRegex.EMAIL)
          if (email != null && name != null) {
            val emailMatcher = EmailRegex.PATTERN.matcher(name)
            if (emailMatcher.matches() && email == emailMatcher.group(EmailRegex.EMAIL)) {
              email = emailMatcher.group(EmailRegex.EMAIL)
              name = null
            }
          }
          if (email == null && name != null) {
            val emailMatcher = EmailRegex.PATTERN.matcher(name)
            if (emailMatcher.matches()) {
              email = emailMatcher.group(EmailRegex.EMAIL)
            }
          }
          return email
        }
      }
      return null
    }
  }
}

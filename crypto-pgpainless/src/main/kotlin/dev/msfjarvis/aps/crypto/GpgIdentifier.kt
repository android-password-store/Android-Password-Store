/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import java.util.regex.Pattern

public sealed class GpgIdentifier {
  public data class KeyId(val id: Long) : GpgIdentifier() {
    override fun toString(): String {
      return java.lang.Long.toHexString(id)
    }
  }
  public data class UserId(val email: String) : GpgIdentifier() {
    override fun toString(): String {
      return email
    }
  }

  public companion object {
    @OptIn(ExperimentalUnsignedTypes::class)
    public fun fromString(identifier: String): GpgIdentifier? {
      if (identifier.isEmpty()) return null
      // Match long key IDs:
      // FF22334455667788 or 0xFF22334455667788
      val maybeLongKeyId =
        identifier.removePrefix("0x").takeIf { it.matches("[a-fA-F0-9]{16}".toRegex()) }
      if (maybeLongKeyId != null) {
        val keyId = maybeLongKeyId.toULong(16)
        return KeyId(keyId.toLong())
      }

      // Match fingerprints:
      // FF223344556677889900112233445566778899 or 0xFF223344556677889900112233445566778899
      val maybeFingerprint =
        identifier.removePrefix("0x").takeIf { it.matches("[a-fA-F0-9]{40}".toRegex()) }
      if (maybeFingerprint != null) {
        // Truncating to the long key ID is not a security issue since OpenKeychain only
        // accepts
        // non-ambiguous key IDs.
        val keyId = maybeFingerprint.takeLast(16).toULong(16)
        return KeyId(keyId.toLong())
      }

      return splitUserId(identifier)?.let { UserId(it) }
    }

    private val USER_ID_PATTERN = Pattern.compile("^(.*?)(?: \\((.*)\\))?(?: <(.*)>)?$")
    private val EMAIL_PATTERN = Pattern.compile("^<?\"?([^<>\"]*@[^<>\"]*[.]?[^<>\"]*)\"?>?$")

    /**
     * Takes a 'Name (Comment) <Email>' user ID in any of its permutations and attempts to extract
     * an email from it.
     */
    private fun splitUserId(userId: String): String? {
      if (userId.isNotEmpty()) {
        val matcher = USER_ID_PATTERN.matcher(userId)
        if (matcher.matches()) {
          var name = if (matcher.group(1)?.isEmpty() == true) null else matcher.group(1)
          var email = matcher.group(3)
          if (email != null && name != null) {
            val emailMatcher = EMAIL_PATTERN.matcher(name)
            if (emailMatcher.matches() && email == emailMatcher.group(1)) {
              email = emailMatcher.group(1)
              name = null
            }
          }
          if (email == null && name != null) {
            val emailMatcher = EMAIL_PATTERN.matcher(name)
            if (emailMatcher.matches()) {
              email = emailMatcher.group(1)
            }
          }
          return email
        }
      }
      return null
    }
  }
}

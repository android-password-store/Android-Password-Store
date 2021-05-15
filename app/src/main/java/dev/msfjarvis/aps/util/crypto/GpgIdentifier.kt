/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.crypto

import me.msfjarvis.openpgpktx.util.OpenPgpUtils

sealed class GpgIdentifier {
  data class KeyId(val id: Long) : GpgIdentifier()
  data class UserId(val email: String) : GpgIdentifier()

  companion object {
    @OptIn(ExperimentalUnsignedTypes::class)
    fun fromString(identifier: String): GpgIdentifier? {
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

      return OpenPgpUtils.splitUserId(identifier).email?.let { UserId(it) }
    }
  }
}

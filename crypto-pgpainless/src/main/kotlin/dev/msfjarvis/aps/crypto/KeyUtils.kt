/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.get
import com.github.michaelbull.result.runCatching
import java.util.Locale
import org.bouncycastle.openpgp.PGPKeyRing
import org.pgpainless.PGPainless

/** Utility methods to deal with PGP [Key]s. */
public object KeyUtils {
  /**
   * Attempts to parse a [PGPKeyRing] from a given [key]. The key is first tried as a secret key and
   * then as a public one before the method gives up and returns null.
   */
  public fun tryParseKeyring(key: Key): PGPKeyRing? {
    val secKeyRing = runCatching { PGPainless.readKeyRing().secretKeyRing(key.contents) }.get()
    if (secKeyRing != null) {
      return secKeyRing
    }
    val pubKeyRing = runCatching { PGPainless.readKeyRing().publicKeyRing(key.contents) }.get()
    if (pubKeyRing != null) {
      return pubKeyRing
    }
    return null
  }

  /** Parses a [PGPKeyRing] from the given [key] and returns its hex-formatted key ID. */
  public fun tryGetId(key: Key): String? {
    val keyRing = tryParseKeyring(key) ?: return null
    return convertKeyIdToHex(keyRing.publicKey.keyID)
  }

  /** Convert a [Long] key ID to a formatted string. */
  private fun convertKeyIdToHex(keyId: Long): String {
    return "0x" + convertKeyIdToHex32bit(keyId shr 32) + convertKeyIdToHex32bit(keyId)
  }

  /**
   * Converts [keyId] to an unsigned [Long] then uses [java.lang.Long.toHexString] to convert it to
   * a lowercase hex ID.
   */
  private fun convertKeyIdToHex32bit(keyId: Long): String {
    var hexString = java.lang.Long.toHexString(keyId and 0xffffffffL).lowercase(Locale.ENGLISH)
    while (hexString.length < 8) {
      hexString = "0$hexString"
    }
    return hexString
  }
}

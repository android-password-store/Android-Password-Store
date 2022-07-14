/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.GpgIdentifier.KeyId
import com.github.michaelbull.result.get
import com.github.michaelbull.result.runCatching
import org.bouncycastle.openpgp.PGPKeyRing
import org.pgpainless.PGPainless

/** Utility methods to deal with [PGPKey]s. */
public object KeyUtils {
  /**
   * Attempts to parse a [PGPKeyRing] from a given [key]. The key is first tried as a secret key and
   * then as a public one before the method gives up and returns null.
   */
  public fun tryParseKeyring(key: PGPKey): PGPKeyRing? {
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

  /** Parses a [PGPKeyRing] from the given [key] and calculates its long key ID */
  public fun tryGetId(key: PGPKey): KeyId? {
    val keyRing = tryParseKeyring(key) ?: return null
    return KeyId(keyRing.publicKey.keyID)
  }
}

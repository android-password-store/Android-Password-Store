/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.PGPIdentifier.KeyId
import app.passwordstore.crypto.PGPIdentifier.UserId
import com.github.michaelbull.result.get
import com.github.michaelbull.result.runCatching
import org.bouncycastle.openpgp.PGPKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.key.parsing.KeyRingReader

/** Utility methods to deal with [PGPKey]s. */
public object KeyUtils {
  /**
   * Attempts to parse a [PGPKeyRing] from a given [key]. The key is first tried as a secret key and
   * then as a public one before the method gives up and returns null.
   */
  public fun tryParseKeyring(key: PGPKey): PGPKeyRing? {
    return runCatching { KeyRingReader.readKeyRing(key.contents.inputStream()) }.get()
  }

  /** Parses a [PGPKeyRing] from the given [key] and calculates its long key ID */
  public fun tryGetId(key: PGPKey): KeyId? {
    val keyRing = tryParseKeyring(key) ?: return null
    return KeyId(keyRing.publicKey.keyID)
  }

  /**
   * Attempts to parse the given [PGPKey] into a [PGPKeyRing] and obtains the [UserId] of the
   * corresponding public key.
   */
  public fun tryGetEmail(key: PGPKey): UserId? {
    val keyRing = tryParseKeyring(key) ?: return null
    return UserId(keyRing.publicKey.userIDs.next())
  }

  /**
   * Tests if the given [key] can be used for encryption, which is a bare minimum necessity for the
   * app.
   */
  public fun isKeyUsable(key: PGPKey): Boolean {
    return runCatching {
        val keyRing = tryParseKeyring(key) ?: return false
        PGPainless.inspectKeyRing(keyRing).isUsableForEncryption
      }
      .get() != null
  }
}

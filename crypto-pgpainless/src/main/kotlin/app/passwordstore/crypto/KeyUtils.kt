/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.GpgIdentifier.KeyId
import app.passwordstore.crypto.GpgIdentifier.UserId
import com.github.michaelbull.result.get
import com.github.michaelbull.result.runCatching
import org.bouncycastle.openpgp.PGPKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.algorithm.EncryptionPurpose
import org.pgpainless.key.OpenPgpFingerprint
import org.pgpainless.key.info.KeyRingInfo
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

  public fun tryGetEmail(key: PGPKey): UserId? {
    val keyRing = tryParseKeyring(key) ?: return null
    return UserId(keyRing.publicKey.userIDs.next())
  }
  public fun tryGetEncryptionKeyFingerprint(key: PGPKey): OpenPgpFingerprint? {
    val keyRing = tryParseKeyring(key) ?: return null
    val encryptionSubkey =
      KeyRingInfo(keyRing).getEncryptionSubkeys(EncryptionPurpose.ANY).lastOrNull()
    return encryptionSubkey?.let(OpenPgpFingerprint::of)
  }

  public fun tryGetEncryptionKey(key: PGPKey): PGPSecretKey? {
    val keyRing = tryParseKeyring(key) as? PGPSecretKeyRing ?: return null
    return tryGetEncryptionKey(keyRing)
  }

  public fun tryGetEncryptionKey(keyRing: PGPSecretKeyRing): PGPSecretKey? {
    val info = KeyRingInfo(keyRing)
    return tryGetEncryptionKey(info)
  }

  private fun tryGetEncryptionKey(info: KeyRingInfo): PGPSecretKey? {
    val encryptionKey = info.getEncryptionSubkeys(EncryptionPurpose.ANY).lastOrNull() ?: return null
    return info.getSecretKey(encryptionKey.keyID)
  }
}

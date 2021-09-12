/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import org.bouncycastle.openpgp.PGPSecretKey

public class PGPKeyPair(private val secretKey: PGPSecretKey) : KeyPair {

  init {
    if (secretKey.isPrivateKeyEmpty) throw KeyPairException.PrivateKeyUnavailableException
  }

  override fun getPrivateKey(): ByteArray {
    return secretKey.encoded
  }
  override fun getPublicKey(): ByteArray {
    return secretKey.publicKey.encoded
  }
  override fun getKeyId(): String {
    return secretKey.keyID.toString(radix = 16)
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.crypto

import com.proton.Gopenpgp.crypto.Crypto
import com.proton.Gopenpgp.helper.Helper
import javax.inject.Inject

/** Gopenpgp backed implementation of [CryptoHandler]. */
public class GopenpgpCryptoHandler @Inject constructor() : CryptoHandler {

  /**
   * Decrypt the given [ciphertext] using the given PGP [privateKey] and corresponding [passphrase].
   */
  override fun decrypt(
    privateKey: String,
    passphrase: ByteArray,
    ciphertext: ByteArray,
  ): ByteArray {
    val message = Crypto.newPGPMessage(ciphertext)
    return Helper.decryptBinaryMessageArmored(
      privateKey,
      passphrase,
      message.armored,
    )
  }

  override fun encrypt(publicKey: String, plaintext: ByteArray): String {
    return Helper.encryptBinaryMessageArmored(
      publicKey,
      plaintext,
    )
  }
}

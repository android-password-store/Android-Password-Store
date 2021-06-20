/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.crypto

/** Generic interface to implement cryptographic operations on top of. Naming is up for debate. */
public interface CryptoHandler {

  /**
   * Decrypt the given [ciphertext] using a [privateKey] and [passphrase], returning a [ByteArray]
   * corresponding to the decrypted plaintext.
   */
  public fun decrypt(privateKey: String, passphrase: ByteArray, ciphertext: ByteArray): ByteArray

  /**
   * Encrypt the given [plaintext] to the provided [publicKey], returning the encrypted ciphertext
   * as a [String]
   */
  public fun encrypt(publicKey: String, plaintext: ByteArray): String
}

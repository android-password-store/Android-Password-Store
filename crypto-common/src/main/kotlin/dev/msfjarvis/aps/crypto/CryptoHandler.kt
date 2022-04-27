/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.Result
import dev.msfjarvis.aps.crypto.errors.CryptoHandlerException
import java.io.InputStream
import java.io.OutputStream

/** Generic interface to implement cryptographic operations on top of. */
public interface CryptoHandler<Key> {

  /**
   * Decrypt the given [ciphertextStream] using a [privateKey] and [passphrase], and writes the
   * resultant plaintext to [outputStream]. The returned [Result] should be checked to ensure it is
   * **not** an instance of [com.github.michaelbull.result.Err] before the contents of
   * [outputStream] are used.
   */
  public fun decrypt(
    privateKey: Key,
    passphrase: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
  ): Result<Unit, CryptoHandlerException>

  /**
   * Encrypt the given [plaintextStream] to the provided [keys], and writes the encrypted ciphertext
   * to [outputStream]. The returned [Result] should be checked to ensure it is **not** an instance
   * of [com.github.michaelbull.result.Err] before the contents of [outputStream] are used.
   */
  public fun encrypt(
    keys: List<Key>,
    plaintextStream: InputStream,
    outputStream: OutputStream,
  ): Result<Unit, CryptoHandlerException>

  /** Given a [fileName], return whether this instance can handle it. */
  public fun canHandle(fileName: String): Boolean
}

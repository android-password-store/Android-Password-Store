/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import java.io.InputStream
import java.io.OutputStream

/** Generic interface to implement cryptographic operations on top of. */
public interface CryptoHandler {

  /**
   * Decrypt the given [ciphertextStream] using a [privateKey] and [password], and writes the
   * resultant plaintext to [outputStream].
   */
  public fun decrypt(
    privateKey: String,
    password: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
  )

  /**
   * Encrypt the given [plaintextStream] to the provided [pubKeys], and writes the encrypted
   * ciphertext to [outputStream].
   */
  public fun encrypt(
    pubKeys: List<String>,
    plaintextStream: InputStream,
    outputStream: OutputStream,
  )

  /** Given a [fileName], return whether this instance can handle it. */
  public fun canHandle(fileName: String): Boolean
}

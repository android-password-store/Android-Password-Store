/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.crypto

import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrap
import dev.msfjarvis.aps.crypto.PGPKeyManager
import dev.msfjarvis.aps.crypto.PGPainlessCryptoHandler
import dev.msfjarvis.aps.util.extensions.isOk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CryptoRepository
@Inject
constructor(
  private val pgpKeyManager: PGPKeyManager,
  private val pgpCryptoHandler: PGPainlessCryptoHandler,
) {

  suspend fun decrypt(
    password: String,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) {
    withContext(Dispatchers.IO) { decryptPgp(password, message, out) }
  }

  suspend fun encrypt(content: ByteArrayInputStream, out: ByteArrayOutputStream) {
    withContext(Dispatchers.IO) { encryptPgp(content, out) }
  }

  private suspend fun decryptPgp(
    password: String,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) {
    val keys = pgpKeyManager.getAllKeys().unwrap()
    // Iterates through the keys until the first successful decryption, then returns.
    keys.first { key ->
      runCatching { pgpCryptoHandler.decrypt(key, password, message, out) }.isOk()
    }
  }

  private suspend fun encryptPgp(content: ByteArrayInputStream, out: ByteArrayOutputStream) {
    val keys = pgpKeyManager.getAllKeys().unwrap()
    pgpCryptoHandler.encrypt(
      keys,
      content,
      out,
    )
  }
}

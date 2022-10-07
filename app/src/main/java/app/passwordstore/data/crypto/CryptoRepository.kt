/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.crypto

import app.passwordstore.crypto.GpgIdentifier
import app.passwordstore.crypto.PGPKeyManager
import app.passwordstore.crypto.PGPainlessCryptoHandler
import app.passwordstore.crypto.errors.CryptoHandlerException
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getAll
import com.github.michaelbull.result.unwrap
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
  ) = withContext(Dispatchers.IO) { decryptPgp(password, message, out) }

  suspend fun encrypt(
    identities: List<GpgIdentifier>,
    content: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) = withContext(Dispatchers.IO) { encryptPgp(identities, content, out) }

  private suspend fun decryptPgp(
    password: String,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ): Result<Unit, CryptoHandlerException> {
    val keys = pgpKeyManager.getAllKeys().unwrap()
    return pgpCryptoHandler.decrypt(keys, password, message, out)
  }

  private suspend fun encryptPgp(
    identities: List<GpgIdentifier>,
    content: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ): Result<Unit, CryptoHandlerException> {
    val keys = identities.map { id -> pgpKeyManager.getKeyById(id) }.getAll()
    return pgpCryptoHandler.encrypt(
      keys,
      content,
      out,
    )
  }
}

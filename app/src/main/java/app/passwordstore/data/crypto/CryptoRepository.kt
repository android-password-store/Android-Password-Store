/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.crypto

import android.content.SharedPreferences
import app.passwordstore.crypto.PGPDecryptOptions
import app.passwordstore.crypto.PGPEncryptOptions
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.crypto.PGPKeyManager
import app.passwordstore.crypto.PGPainlessCryptoHandler
import app.passwordstore.crypto.errors.CryptoHandlerException
import app.passwordstore.injection.prefs.SettingsPreferences
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getAll
import com.github.michaelbull.result.mapBoth
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext

class CryptoRepository
@Inject
constructor(
  private val pgpKeyManager: PGPKeyManager,
  private val pgpCryptoHandler: PGPainlessCryptoHandler,
  private val dispatcherProvider: DispatcherProvider,
  @SettingsPreferences private val settings: SharedPreferences,
) {

  suspend fun hasKeys(): Boolean {
    return withContext(dispatcherProvider.io()) {
      pgpKeyManager.getAllKeys().mapBoth(success = { it.isNotEmpty() }, failure = { false })
    }
  }

  suspend fun decrypt(
    password: String,
    identities: List<PGPIdentifier>,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) = withContext(dispatcherProvider.io()) { decryptPgp(password, identities, message, out) }

  fun isPasswordProtected(message: ByteArrayInputStream): Boolean {
    return pgpCryptoHandler.isPassphraseProtected(message)
  }

  suspend fun encrypt(
    identities: List<PGPIdentifier>,
    content: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) = withContext(dispatcherProvider.io()) { encryptPgp(identities, content, out) }

  private suspend fun decryptPgp(
    password: String,
    identities: List<PGPIdentifier>,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ): Result<Unit, CryptoHandlerException> {
    val keys = identities.map { id -> pgpKeyManager.getKeyById(id) }.getAll()
    val decryptionOptions = PGPDecryptOptions.Builder().build()
    return pgpCryptoHandler.decrypt(keys, password, message, out, decryptionOptions)
  }

  private suspend fun encryptPgp(
    identities: List<PGPIdentifier>,
    content: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ): Result<Unit, CryptoHandlerException> {
    val encryptionOptions =
      PGPEncryptOptions.Builder()
        .withAsciiArmor(settings.getBoolean(PreferenceKeys.ASCII_ARMOR, false))
        .build()
    val keys = identities.map { id -> pgpKeyManager.getKeyById(id) }.getAll()
    return pgpCryptoHandler.encrypt(
      keys,
      content,
      out,
      encryptionOptions,
    )
  }
}

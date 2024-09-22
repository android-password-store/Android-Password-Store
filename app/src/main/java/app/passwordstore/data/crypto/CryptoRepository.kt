/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.crypto

import android.content.SharedPreferences
import app.passwordstore.crypto.PGPDecryptOptions
import app.passwordstore.crypto.PGPEncryptOptions
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.crypto.PGPKeyManager
import app.passwordstore.crypto.PGPainlessCryptoHandler
import app.passwordstore.injection.prefs.SettingsPreferences
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.filterValues
import com.github.michaelbull.result.map
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

  suspend fun isPasswordProtected(identifiers: List<PGPIdentifier>): Boolean {
    val keys = identifiers.map { pgpKeyManager.getKeyById(it) }.filterValues()
    return pgpCryptoHandler.isPassphraseProtected(keys)
  }

  suspend fun decrypt(
    password: String,
    identities: List<PGPIdentifier>,
    message: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) =
    withContext(dispatcherProvider.io()) {
      val keys = identities.map { id -> pgpKeyManager.getKeyById(id) }.filterValues()
      val decryptionOptions = PGPDecryptOptions.Builder().build()
      pgpCryptoHandler.decrypt(keys, password, message, out, decryptionOptions).map { out }
    }

  suspend fun encrypt(
    identities: List<PGPIdentifier>,
    content: ByteArrayInputStream,
    out: ByteArrayOutputStream,
  ) =
    withContext(dispatcherProvider.io()) {
      val encryptionOptions =
        PGPEncryptOptions.Builder()
          .withAsciiArmor(settings.getBoolean(PreferenceKeys.ASCII_ARMOR, false))
          .build()
      val keys = identities.map { id -> pgpKeyManager.getKeyById(id) }.filterValues()
      pgpCryptoHandler.encrypt(keys, content, out, encryptionOptions).map { out }
    }
}

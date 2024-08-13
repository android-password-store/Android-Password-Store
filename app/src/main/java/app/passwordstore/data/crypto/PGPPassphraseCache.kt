/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.crypto

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.getString
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Implements a rudimentary [EncryptedSharedPreferences]-backed cache for GPG passphrases. */
class PGPPassphraseCache @Inject constructor(private val dispatcherProvider: DispatcherProvider) {

  suspend fun cachePassphrase(context: Context, identifier: PGPIdentifier, passphrase: String) {
    withContext(dispatcherProvider.io()) {
      getPreferences(context).edit { putString(identifier.toString(), passphrase) }
    }
  }

  suspend fun retrieveCachedPassphrase(context: Context, identifier: PGPIdentifier): String? {
    return withContext(dispatcherProvider.io()) {
      getPreferences(context).getString(identifier.toString())
    }
  }

  suspend fun clearCachedPassphrase(context: Context, identifier: PGPIdentifier) {
    withContext(dispatcherProvider.io()) {
      getPreferences(context).edit { remove(identifier.toString()) }
    }
  }

  suspend fun clearAllCachedPassphrases(context: Context) {
    withContext(dispatcherProvider.io()) { getPreferences(context).edit { clear() } }
  }

  private suspend fun getPreferences(context: Context) =
    withContext(dispatcherProvider.io()) {
      EncryptedSharedPreferences.create(
        context,
        ANDROIDX_SECURITY_KEYSET_PREF_NAME,
        getOrCreateWrappingMasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    }

  private suspend fun getOrCreateWrappingMasterKey(context: Context) =
    withContext(dispatcherProvider.io()) {
      MasterKey.Builder(context, "passphrase")
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .setUserAuthenticationRequired(
          /* authenticationRequired = */ true,
          /* userAuthenticationValidityDurationSeconds = */ 60,
        )
        .build()
    }

  private companion object {

    private const val ANDROIDX_SECURITY_KEYSET_PREF_NAME = "androidx_passphrase_keyset_prefs"
  }
}

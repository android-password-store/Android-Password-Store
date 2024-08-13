/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.passwordstore.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class PreferenceModule {

  private fun createEncryptedPreferences(context: Context, fileName: String): SharedPreferences {
    val masterKeyAlias =
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(
      context,
      fileName,
      masterKeyAlias,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  @[Provides PasswordGeneratorPreferences Reusable]
  fun providePwgenPreferences(@ApplicationContext context: Context): SharedPreferences {
    return createEncryptedPreferences(context, "pwgen_preferences")
  }

  @[Provides SettingsPreferences Reusable]
  fun provideSettingsPreferences(@ApplicationContext context: Context): SharedPreferences {
    return context.getSharedPreferences("${BuildConfig.APPLICATION_ID}_preferences", MODE_PRIVATE)
  }

  @[Provides GitPreferences Reusable]
  fun provideGitPreferences(@ApplicationContext context: Context): SharedPreferences {
    return createEncryptedPreferences(context, "git_operation")
  }

  @[Provides ProxyPreferences Reusable]
  fun provideProxyPreferences(@ApplicationContext context: Context): SharedPreferences {
    return createEncryptedPreferences(context, "http_proxy")
  }
}

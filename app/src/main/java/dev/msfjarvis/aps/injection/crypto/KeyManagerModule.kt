/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection.crypto

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.msfjarvis.aps.crypto.PGPKeyManager
import javax.inject.Qualifier
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object KeyManagerModule {
  @Provides
  fun providePGPKeyManager(
    @PGPKeyDir keyDir: String,
  ): PGPKeyManager {
    return PGPKeyManager(
      keyDir,
      Dispatchers.IO,
    )
  }

  @Provides
  @PGPKeyDir
  fun providePGPKeyDir(@ApplicationContext context: Context): String {
    return context.filesDir.resolve("pgp_keys").absolutePath
  }
}

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class PGPKeyDir

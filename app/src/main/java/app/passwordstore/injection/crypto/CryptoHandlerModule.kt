/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.crypto

import app.passwordstore.crypto.PGPainlessCryptoHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CryptoHandlerModule {
  @Provides fun providePgpCryptoHandler() = PGPainlessCryptoHandler()
}

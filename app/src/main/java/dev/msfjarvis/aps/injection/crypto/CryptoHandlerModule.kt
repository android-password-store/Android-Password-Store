/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection.crypto

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.msfjarvis.aps.crypto.PGPainlessCryptoHandler

@Module
@InstallIn(SingletonComponent::class)
object CryptoHandlerModule {
  @Provides fun providePgpCryptoHandler() = PGPainlessCryptoHandler()
}

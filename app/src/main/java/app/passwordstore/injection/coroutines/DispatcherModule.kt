/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.coroutines

import app.passwordstore.util.coroutines.DefaultDispatcherProvider
import app.passwordstore.util.coroutines.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DispatcherModule {
  @Binds fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection.coroutines

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.msfjarvis.aps.util.coroutines.DefaultDispatcherProvider
import dev.msfjarvis.aps.util.coroutines.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
interface DispatcherModule {
  @Binds fun DefaultDispatcherProvider.bind(): DispatcherProvider
}

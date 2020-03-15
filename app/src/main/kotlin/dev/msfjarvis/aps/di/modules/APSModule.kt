/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di.modules

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dev.msfjarvis.aps.di.factory.SSHClientFactory
import javax.inject.Singleton

@Module
object APSModule {
  @Provides
  @Singleton
  fun provideSharedPrefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  @Provides
  @Singleton
  fun provideSSHClient(context: Context) = SSHClientFactory.provideSSHClient(context)
}

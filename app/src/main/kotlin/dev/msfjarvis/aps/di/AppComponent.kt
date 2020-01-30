/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import dev.msfjarvis.aps.ui.activity.SplashActivity
import javax.inject.Singleton

@Component(modules = [APSModule::class])
@Singleton
interface AppComponent {
  @Component.Factory
  interface Factory {
    fun create(@BindsInstance context: Context): AppComponent
  }

  // Activities
  fun inject(activity: SplashActivity)
}

@Module
object APSModule {
  @Provides
  @Singleton
  fun provideSharedPrefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
}

/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import dev.msfjarvis.aps.di.modules.APSModule
import dev.msfjarvis.aps.di.modules.RoomModule
import dev.msfjarvis.aps.ui.firstrun.activity.FirstRunActivity
import dev.msfjarvis.aps.ui.firstrun.viewmodels.FirstRunViewModel
import dev.msfjarvis.aps.ui.splash.activity.SplashActivity
import javax.inject.Singleton

@Component(modules = [APSModule::class, RoomModule::class])
@Singleton
interface AppComponent {
  @Component.Factory
  interface Factory {
    fun create(@BindsInstance context: Context): AppComponent
  }

  val firstRunViewModel: FirstRunViewModel

  // Activities
  fun inject(activity: SplashActivity)
  fun inject(activity: FirstRunActivity)
}

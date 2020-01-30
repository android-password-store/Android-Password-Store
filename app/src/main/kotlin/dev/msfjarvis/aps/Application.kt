/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps

import dev.msfjarvis.aps.di.AppComponent
import dev.msfjarvis.aps.di.DaggerAppComponent
import dev.msfjarvis.aps.di.InjectorProvider

class Application : android.app.Application(), InjectorProvider {
  override val component: AppComponent by lazy { DaggerAppComponent.factory().create(applicationContext) }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.crypto

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import app.passwordstore.crypto.HWSecurityDeviceHandler
import app.passwordstore.crypto.HWSecurityManager
import app.passwordstore.crypto.PGPainlessCryptoHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object CryptoHandlerModule {

  @Provides
  @ActivityScoped
  fun provideDeviceHandler(
    activity: Activity,
    deviceManager: HWSecurityManager
  ): HWSecurityDeviceHandler = HWSecurityDeviceHandler(
    deviceManager = deviceManager,
    fragmentManager = (activity as FragmentActivity).supportFragmentManager
  )

  @Provides fun providePgpCryptoHandler() = PGPainlessCryptoHandler()
}

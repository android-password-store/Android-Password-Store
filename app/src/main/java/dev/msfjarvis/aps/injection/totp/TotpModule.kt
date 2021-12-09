/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection.totp

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dev.msfjarvis.aps.util.totp.TotpFinder
import dev.msfjarvis.aps.util.totp.UriTotpFinder

@Module
@InstallIn(ActivityComponent::class)
interface TotpModule {
  @Binds fun UriTotpFinder.bind(): TotpFinder
}

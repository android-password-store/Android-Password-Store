/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection

import android.os.Build
import app.passwordstore.util.autofill.Api26AutofillResponseBuilder
import app.passwordstore.util.autofill.Api30AutofillResponseBuilder
import app.passwordstore.util.autofill.AutofillResponseBuilder
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AutofillResponseBuilderModule {

  @Provides
  @Reusable
  fun provideAutofillResponseBuilder(): AutofillResponseBuilder.Factory {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Api30AutofillResponseBuilder.Factory
    } else {
      Api26AutofillResponseBuilder.Factory
    }
  }
}

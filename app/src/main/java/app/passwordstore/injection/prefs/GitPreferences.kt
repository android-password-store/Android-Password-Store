/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.prefs

import android.content.SharedPreferences
import javax.inject.Qualifier

/**
 * Qualifies a [SharedPreferences] instance specifically used for encrypted Git-related settings.
 */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class GitPreferences

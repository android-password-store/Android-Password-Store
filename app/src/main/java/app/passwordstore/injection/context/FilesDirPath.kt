/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.injection.context

import android.content.Context
import javax.inject.Qualifier

/** Qualifies a [String] representing the absolute path of [Context.getFilesDir]. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class FilesDirPath

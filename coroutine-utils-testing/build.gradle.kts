/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins { id("com.github.android-password-store.kotlin-jvm-library") }

dependencies {
  implementation(projects.coroutineUtils)
  implementation(libs.testing.junit)
  implementation(libs.kotlin.coroutines.test)
}

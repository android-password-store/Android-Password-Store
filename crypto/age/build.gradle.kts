/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins { id("com.github.android-password-store.kotlin-jvm-library") }

dependencies {
  api(projects.crypto.common)
  implementation(libs.androidx.annotation)
  implementation(libs.aps.kage)
  implementation(libs.dagger.hilt.core)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.thirdparty.kotlinResult.coroutines)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.testing.testparameterinjector)
}

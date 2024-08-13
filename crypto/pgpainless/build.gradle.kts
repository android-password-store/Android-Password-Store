/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins { id("com.github.android-password-store.kotlin-jvm-library") }

dependencies {
  api(projects.crypto.common)
  implementation(libs.androidx.annotation)
  implementation(libs.dagger.hilt.core)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.thirdparty.kotlinResult.coroutines)
  implementation(libs.thirdparty.pgpainless)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.testing.testparameterinjector)
}

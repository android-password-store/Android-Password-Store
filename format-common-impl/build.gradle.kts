/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android { namespace = "app.passwordstore.format.common.impl" }

dependencies {
  api(projects.formatCommon)
  implementation(libs.dagger.hilt.core)
  testImplementation(projects.coroutineUtilsTesting)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.testing.robolectric)
  testImplementation(libs.testing.turbine)
}

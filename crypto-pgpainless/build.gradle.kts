/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

plugins {
  kotlin("jvm")
  id("com.github.android-password-store.kotlin-library")
}

dependencies {
  api(projects.cryptoCommon)
  implementation(projects.coroutineUtils)
  implementation(libs.androidx.annotation)
  implementation(libs.dagger.hilt.core)
  implementation(libs.kotlin.coroutines.core)
  implementation(libs.thirdparty.kotlinResult)
  api(libs.thirdparty.pgpainless)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.testing.testparameterinjector)
}

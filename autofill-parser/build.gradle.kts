/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */
@file:Suppress("UnstableApiUsage")

plugins {
  id("com.github.android-password-store.published-android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.psl-plugin")
}

android {
  defaultConfig {
    minSdk = 23
    consumerProguardFiles("consumer-rules.pro")
  }
  sourceSets { getByName("test") { resources.srcDir("src/main/assets") } }
  namespace = "com.github.androidpasswordstore.autofillparser"
}

dependencies {
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.thirdparty.logcat)
  testImplementation(libs.bundles.testDependencies)
}

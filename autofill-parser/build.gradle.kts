/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */

plugins {
  id("com.github.android-password-store.published-android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
  id("com.github.android-password-store.psl-plugin")
}

android {
  defaultConfig { consumerProguardFiles("consumer-rules.pro") }
  sourceSets { getByName("test") { resources.srcDir("src/main/assets") } }
}

dependencies {
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.autofill)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)
  implementation(libs.thirdparty.logcat)
  testImplementation(libs.bundles.testDependencies)
}

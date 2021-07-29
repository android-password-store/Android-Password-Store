/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */

plugins {
  id("com.android.library")
  id("com.vanniktech.maven.publish")
  kotlin("android")
  `aps-plugin`
}

android { defaultConfig { consumerProguardFiles("consumer-rules.pro") } }

dependencies {
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.autofill)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)
  implementation(libs.thirdparty.timberkt)
}

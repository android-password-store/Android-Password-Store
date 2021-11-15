/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("com.android.library")
  id("com.vanniktech.maven.publish")
  kotlin("android")
  id("org.jetbrains.dokka")
  id("com.github.android-password-store.kotlin-common")
  `aps-plugin`
}

android {
  defaultConfig { consumerProguardFiles("consumer-proguard-rules.pro") }

  buildFeatures.aidl = true
}

dependencies { implementation(libs.kotlin.coroutines.core) }

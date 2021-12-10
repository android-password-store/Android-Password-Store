/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("com.github.android-password-store.published-android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android {
  defaultConfig { consumerProguardFiles("consumer-proguard-rules.pro") }

  buildFeatures.aidl = true
}

dependencies { implementation(libs.kotlin.coroutines.core) }

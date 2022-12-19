/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android {
  namespace = "app.passwordstore.crypto.hwsecurity"
}

dependencies {
  implementation(projects.cryptoPgpainless)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.material)
  implementation(libs.aps.hwsecurity.openpgp)
  implementation(libs.aps.hwsecurity.ui)
  implementation(libs.dagger.hilt.android)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.thirdparty.kotlinResult)
}

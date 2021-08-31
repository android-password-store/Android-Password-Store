/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
  kotlin("jvm")
  `aps-plugin`
}

dependencies {
  api(projects.cryptoCommon)
  implementation(libs.dagger.hilt.core)
  implementation(libs.kotlin.coroutines.core)
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.thirdparty.pgpainless)
}

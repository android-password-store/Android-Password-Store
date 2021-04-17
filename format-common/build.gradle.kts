/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  kotlin("jvm")
  `aps-plugin`
}

dependencies {
  compileOnly(libs.androidx.annotation)
  implementation(libs.dagger.hilt.core)
  implementation(libs.thirdparty.commons.codec)
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.kotlin.coroutines.core)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.kotlin.coroutines.test)
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
  `kotlin-dsl`
  `kotlin-dsl-precompiled-script-plugins`
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.binarycompat)
  implementation(libs.build.kover)
  implementation(libs.build.kotlin)
  implementation(libs.build.spotless)
}

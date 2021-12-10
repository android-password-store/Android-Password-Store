/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
  `kotlin-dsl`
  `kotlin-dsl-precompiled-script-plugins`
}

gradlePlugin {
  plugins {
    register("versioning") {
      id = "com.github.android-password-store.versioning-plugin"
      implementationClass = "versioning.VersioningPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.dokka)
  implementation(libs.build.mavenpublish)
  implementation(libs.build.semver)
}

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
    register("crowdin") {
      id = "com.github.android-password-store.crowdin-plugin"
      implementationClass = "crowdin.CrowdinDownloadPlugin"
    }
    register("psl") {
      id = "com.github.android-password-store.psl-plugin"
      implementationClass = "psl.PublicSuffixListPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.download)
  implementation(libs.build.okhttp)
}

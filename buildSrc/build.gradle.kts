/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { `kotlin-dsl` }

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
  // For binary compatibility validator.
  maven { url = uri("https://kotlin.bintray.com/kotlinx") }
}

kotlinDslPluginOptions { experimentalWarning.set(false) }

gradlePlugin {
  plugins {
    register("aps") {
      id = "aps-plugin"
      implementationClass = "PasswordStorePlugin"
    }
    register("crowdin") {
      id = "crowdin-plugin"
      implementationClass = "CrowdinDownloadPlugin"
    }
    register("versioning") {
      id = "versioning-plugin"
      implementationClass = "VersioningPlugin"
    }
  }
}

dependencies {
  implementation(Plugins.androidGradlePlugin)
  implementation(Plugins.binaryCompatibilityValidator)
  implementation(Plugins.dokkaPlugin)
  implementation(Plugins.downloadTaskPlugin)
  implementation(Plugins.kotlinGradlePlugin)
  implementation(Plugins.ktfmtGradlePlugin)
  implementation(Plugins.mavenPublishPlugin)
  implementation(Plugins.semver4j)
}

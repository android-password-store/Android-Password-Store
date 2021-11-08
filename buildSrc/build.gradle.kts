/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { `kotlin-dsl` }

repositories {
  mavenCentral()
  gradlePluginPortal()
  google()
}

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
    register("psl") {
      id = "psl-plugin"
      implementationClass = "PublicSuffixListPlugin"
    }
  }
}

dependencies {
  implementation("com.android.tools.build:gradle:7.0.3")
  implementation("com.diffplug.spotless:spotless-plugin-gradle:5.16.0")
  implementation("com.google.dagger:hilt-android-gradle-plugin:2.39.1")
  implementation("com.squareup.okhttp3:okhttp:4.9.0")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.18.0")
  implementation("com.vdurmont:semver4j:3.1.0")
  implementation("de.undercouch:gradle-download-task:4.1.2")
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.5.31")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
  implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.6.0")
}

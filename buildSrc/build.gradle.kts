/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
  `kotlin-dsl`
  id("com.ncorti.ktfmt.gradle") version "0.5.0"
}

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
}

ktfmt {
  googleStyle()
  maxWidth.set(120)
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
  }
}

dependencies {
  implementation("com.android.tools.build:gradle:4.1.3")
  implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.5.0")
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.30")
  implementation("de.undercouch:gradle-download-task:4.1.1")
  implementation("com.google.dagger:hilt-android-gradle-plugin:2.35.1")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
  implementation("com.ncorti.ktfmt.gradle:plugin:0.5.0")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.13.0")
  implementation("com.vdurmont:semver4j:3.1.0")
}

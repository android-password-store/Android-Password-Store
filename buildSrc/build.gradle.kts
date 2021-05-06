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
  // Dokka's transitive kotlinx-html dependency is only published to their Space repo
  // https://github.com/Kotlin/dokka/releases/tag/v1.4.32
  maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") {
    name = "KotlinX HTML Maven"
    content {
      includeModule("org.jetbrains.kotlinx", "kotlinx-html")
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-assembly")
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-common")
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-js")
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-jvm")
    }
  }
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
  implementation("com.android.tools.build:gradle:4.2.0")
  implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.5.0")
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.32")
  implementation("de.undercouch:gradle-download-task:4.1.1")
  implementation("com.google.dagger:hilt-android-gradle-plugin:2.35.1")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
  implementation("com.ncorti.ktfmt.gradle:plugin:0.5.0")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.13.0")
  implementation("com.squareup.okhttp3:okhttp:4.9.0")
  implementation("com.vdurmont:semver4j:3.1.0")
}

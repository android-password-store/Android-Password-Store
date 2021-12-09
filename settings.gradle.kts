/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

// Plugin repositories
pluginManagement {
  repositories {
    includeBuild("build-logic")
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  plugins {
    id("com.vanniktech.maven.publish") version "0.18.0" apply false
    id("org.jetbrains.dokka") version "1.6.0" apply false
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io") {
      name = "JitPack"
      content {
        includeModule("com.github.haroldadmin", "WhatTheStack")
        includeModule("com.github.open-keychain.open-keychain", "sshauthentication-api")
      }
    }
  }
}

// Experimental features
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

enableFeaturePreview("VERSION_CATALOGS")

// Modules
include("app")

include("autofill-parser")

include("coroutine-utils")

include("coroutine-utils-testing")

include("crypto-common")

include("crypto-pgpainless")

include("format-common")

include("openpgp-ktx")

include("dependency-sync")

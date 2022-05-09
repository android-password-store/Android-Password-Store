/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("UnstableApiUsage")

rootProject.name = "APS"

// Plugin repositories
pluginManagement {
  repositories {
    includeBuild("build-logic")
    exclusiveContent {
      forRepository(::google)
      filter {
        includeGroup("androidx.databinding")
        includeGroup("com.android")
        includeGroup("com.android.tools")
        includeGroup("com.android.tools.analytics-library")
        includeGroup("com.android.tools.build")
        includeGroup("com.android.tools.build.jetifier")
        includeGroup("com.android.databinding")
        includeGroup("com.android.tools.ddms")
        includeGroup("com.android.tools.layoutlib")
        includeGroup("com.android.tools.lint")
        includeGroup("com.android.tools.utp")
        includeGroup("com.google.testing.platform")
      }
    }
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    exclusiveContent {
      forRepository(::google)
      filter {
        includeGroup("com.android")
        includeGroup("com.android.tools")
        includeGroup("com.android.tools.analytics-library")
        includeGroup("com.android.tools.build")
        includeGroup("com.android.tools.ddms")
        includeGroup("com.android.tools.external.com-intellij")
        includeGroup("com.android.tools.external.org-jetbrains")
        includeGroup("com.android.tools.layoutlib")
        includeGroup("com.android.tools.lint")
        includeGroup("com.google.android.gms")
        includeModule("com.google.android.material", "material")
        includeGroupByRegex("androidx.*")
      }
    }
    exclusiveContent {
      forRepository { maven("https://jitpack.io") }
      filter {
        includeModule("com.github.haroldadmin", "WhatTheStack")
        includeModule("com.github.open-keychain.open-keychain", "sshauthentication-api")
      }
    }
    mavenCentral()
  }
}

// Experimental features
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Modules
include("app")

include("autofill-parser")

include("coroutine-utils")

include("coroutine-utils-testing")

include("crypto-common")

include("crypto-pgpainless")

include("format-common")

include("openpgp-ktx")

include("passgen:diceware")

include("passgen:random")

include("sentry-stub")

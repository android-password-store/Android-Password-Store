/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("UnstableApiUsage")

rootProject.name = "APS"

// Plugin repositories
pluginManagement {
  repositories {
    includeBuild("build-logic")
    google {
      content {
        includeGroup("androidx.databinding")
        includeGroupByRegex("com.android.*")
        includeGroup("com.google.testing.platform")
      }
    }
    exclusiveContent {
      forRepository { gradlePluginPortal() }
      filter {
        includeModule("com.gradle", "develocity-gradle-plugin")
        includeModule("com.gradle.develocity", "com.gradle.develocity.gradle.plugin")
        includeModule("me.tylerbwong.gradle.metalava", "plugin")
        includeModule(
          "org.gradle.toolchains.foojay-resolver-convention",
          "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
        )
        includeModule("org.gradle.toolchains", "foojay-resolver")
      }
    }
    exclusiveContent {
      forRepository { maven("https://storage.googleapis.com/r8-releases/raw") }
      filter { includeModule("com.android.tools", "r8") }
    }
    mavenCentral { mavenContent { releasesOnly() } }
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  id("com.gradle.develocity") version "3.18.1"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
    termsOfUseAgree = if (System.getenv("GITHUB_WORKFLOW").isNullOrEmpty()) "no" else "yes"
    publishing.onlyIf { !System.getenv("GITHUB_WORKFLOW").isNullOrEmpty() }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com.android.*")
        includeGroup("com.google.android.gms")
        includeModule("com.google.android.material", "material")
      }
    }
    exclusiveContent {
      forRepository { maven("https://storage.googleapis.com/r8-releases/raw") }
      filter { includeModule("com.android.tools", "r8") }
    }
    maven("https://androidx.dev/storage/compose-compiler/repository") {
      name = "Compose Compiler Snapshots"
      content { includeGroup("androidx.compose.compiler") }
    }
    mavenCentral { mavenContent { releasesOnly() } }
  }
}

// Experimental features
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

// Modules
include("app")

include("autofill-parser")

include("coroutine-utils")

include("crypto:common")

include("crypto:pgpainless")

include("format:common")

include("passgen:diceware")

include("passgen:random")

include("sentry-stub")

include("ui:compose")

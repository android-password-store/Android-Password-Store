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
        includeModule("com.github.ben-manes", "gradle-versions-plugin")
        includeModule("com.gradle", "gradle-enterprise-gradle-plugin")
        includeModule("com.gradle.enterprise", "com.gradle.enterprise.gradle.plugin")
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
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
  id("com.gradle.enterprise") version "3.16.1"
  id("com.github.android-password-store.versions")
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = if (System.getenv("GITHUB_WORKFLOW").isNullOrEmpty()) "no" else "yes"
    publishOnFailureIf(!System.getenv("GITHUB_WORKFLOW").isNullOrEmpty())
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    exclusiveContent {
      forRepository { google() }
      filter {
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
    mavenCentral()
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

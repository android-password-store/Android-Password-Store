/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("UnstableApiUsage")

rootProject.name = "build-logic"

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

dependencyResolutionManagement {
  repositories {
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
        includeModule("me.tylerbwong.gradle.metalava", "plugin")
      }
    }
    exclusiveContent {
      forRepository { maven("https://storage.googleapis.com/r8-releases/raw") }
      filter { includeModule("com.android.tools", "r8") }
    }
    mavenCentral { mavenContent { releasesOnly() } }
  }
  versionCatalogs {
    maybeCreate("libs").apply {
      from(files("../gradle/libs.versions.toml"))
      if (System.getenv("DEP_OVERRIDE") == "true") {
        val overrides = System.getenv().filterKeys { it.startsWith("DEP_OVERRIDE_") }
        for ((key, value) in overrides) {
          val catalogKey = key.removePrefix("DEP_OVERRIDE_").lowercase()
          println("Overriding $catalogKey with $value")
          version(catalogKey, value)
        }
      }
    }
  }
}

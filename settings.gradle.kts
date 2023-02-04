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
      forRepository { google() }
      filter {
        includeGroup("androidx.databinding")
        includeGroup("com.android")
        includeGroup("com.android.tools.analytics-library")
        includeGroup("com.android.tools.build")
        includeGroup("com.android.tools.build.jetifier")
        includeGroup("com.android.databinding")
        includeGroup("com.android.tools.ddms")
        includeGroup("com.android.tools.layoutlib")
        includeGroup("com.android.tools.lint")
        includeGroup("com.android.tools.utp")
        includeGroup("com.google.testing.platform")
        includeModule("com.android.tools", "annotations")
        includeModule("com.android.tools", "common")
        includeModule("com.android.tools", "desugar_jdk_libs")
        includeModule("com.android.tools", "desugar_jdk_libs_configuration")
        includeModule("com.android.tools", "dvlib")
        includeModule("com.android.tools", "play-sdk-proto")
        includeModule("com.android.tools", "repository")
        includeModule("com.android.tools", "sdklib")
        includeModule("com.android.tools", "sdk-common")
      }
    }
    exclusiveContent {
      forRepository { gradlePluginPortal() }
      filter {
        includeModule("com.github.ben-manes", "gradle-versions-plugin")
        includeModule("com.gradle", "gradle-enterprise-gradle-plugin")
        includeModule("com.gradle.enterprise", "com.gradle.enterprise.gradle.plugin")
        includeModule("me.tylerbwong.gradle.metalava", "plugin")
      }
    }
    exclusiveContent {
      forRepository { maven("https://storage.googleapis.com/r8-releases/raw") }
      filter { includeModule("com.android.tools", "r8") }
    }
    mavenCentral()
  }
}

plugins { id("com.gradle.enterprise") version "3.12.3" }

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
        includeGroup("androidx.activity")
        includeGroup("androidx.appcompat")
        includeGroup("androidx.annotation")
        includeGroup("androidx.arch.core")
        includeGroup("androidx.autofill")
        includeGroup("androidx.biometric")
        includeGroup("androidx.browser")
        includeGroup("androidx.cardview")
        includeGroup("androidx.collection")
        includeGroup("androidx.compose.animation")
        includeGroup("androidx.compose.foundation")
        includeGroup("androidx.compose.material")
        includeGroup("androidx.compose.material3")
        includeGroup("androidx.compose.runtime")
        includeGroup("androidx.compose.ui")
        includeGroup("androidx.concurrent")
        includeGroup("androidx.constraintlayout")
        includeGroup("androidx.coordinatorlayout")
        includeGroup("androidx.core")
        includeGroup("androidx.cursoradapter")
        includeGroup("androidx.customview")
        includeGroup("androidx.databinding")
        includeGroup("androidx.documentfile")
        includeGroup("androidx.drawerlayout")
        includeGroup("androidx.dynamicanimation")
        includeGroup("androidx.emoji2")
        includeGroup("androidx.exifinterface")
        includeGroup("androidx.fragment")
        includeGroup("androidx.hilt")
        includeGroup("androidx.interpolator")
        includeGroup("androidx.legacy")
        includeGroup("androidx.lifecycle")
        includeGroup("androidx.loader")
        includeGroup("androidx.localbroadcastmanager")
        includeGroup("androidx.preference")
        includeGroup("androidx.print")
        includeGroup("androidx.profileinstaller")
        includeGroup("androidx.recyclerview")
        includeGroup("androidx.resourceinspection")
        includeGroup("androidx.room")
        includeGroup("androidx.savedstate")
        includeGroup("androidx.security")
        includeGroup("androidx.slidingpanelayout")
        includeGroup("androidx.startup")
        includeGroup("androidx.sqlite")
        includeGroup("androidx.swiperefreshlayout")
        includeGroup("androidx.test")
        includeGroup("androidx.test.espresso")
        includeGroup("androidx.tracing")
        includeGroup("androidx.transition")
        includeGroup("androidx.vectordrawable")
        includeGroup("androidx.versionedparcelable")
        includeGroup("androidx.viewpager")
        includeGroup("androidx.viewpager2")
        includeGroup("androidx.window")
        includeGroup("com.android")
        includeGroup("com.android.tools.analytics-library")
        includeGroup("com.android.tools.build")
        includeGroup("com.android.tools.ddms")
        includeGroup("com.android.tools.external.com-intellij")
        includeGroup("com.android.tools.external.org-jetbrains")
        includeGroup("com.android.tools.layoutlib")
        includeGroup("com.android.tools.lint")
        includeGroup("com.google.android.gms")
        includeModule("androidx.compose", "compose-bom")
        includeModule("com.android.tools", "annotations")
        includeModule("com.android.tools", "common")
        includeModule("com.android.tools", "desugar_jdk_libs")
        includeModule("com.android.tools", "desugar_jdk_libs_configuration")
        includeModule("com.android.tools", "dvlib")
        includeModule("com.android.tools", "play-sdk-proto")
        includeModule("com.android.tools", "repository")
        includeModule("com.android.tools", "sdklib")
        includeModule("com.android.tools", "sdk-common")
        includeModule("com.android.tools.metalava", "metalava")
        includeModule("com.google.android.material", "material")
      }
    }
    exclusiveContent {
      forRepository { maven("https://jitpack.io") }
      filter { includeModule("com.github.haroldadmin", "WhatTheStack") }
    }
    exclusiveContent {
      forRepository { maven("https://storage.googleapis.com/r8-releases/raw") }
      filter { includeModule("com.android.tools", "r8") }
    }
    exclusiveContent {
      forRepository { maven("https://androidx.dev/storage/compose-compiler/repository/") }
      filter { includeGroup("androidx.compose.compiler") }
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

include("coroutine-utils-testing")

include("crypto-common")

include("crypto-pgpainless")

include("format-common")

include("passgen:diceware")

include("passgen:random")

include("sentry-stub")

include("ui-compose")

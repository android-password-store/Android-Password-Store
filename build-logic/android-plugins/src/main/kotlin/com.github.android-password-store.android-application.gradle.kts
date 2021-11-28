/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import flavors.FlavorDimensions
import flavors.ProductFlavors
import flavors.configureSlimTests
import org.gradle.kotlin.dsl.configure
import signing.configureBuildSigning

plugins {
  id("com.android.application")
  id("com.github.android-password-store.android-common")
}

fun Project.isSnapshot(): Boolean {
  with(project.providers) {
    val workflow = environmentVariable("GITHUB_WORKFLOW").forUseAtConfigurationTime()
    val snapshot = environmentVariable("SNAPSHOT").forUseAtConfigurationTime()
    return workflow.isPresent || snapshot.isPresent
  }
}

extensions.configure<BaseAppModuleExtension> {
  val minifySwitch =
    project.providers.environmentVariable("DISABLE_MINIFY").forUseAtConfigurationTime()

  adbOptions.installOptions("--user 0")

  buildFeatures {
    viewBinding = true
    buildConfig = true
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = !minifySwitch.isPresent
      setProguardFiles(
        listOf(
          "proguard-android-optimize.txt",
          "proguard-rules.pro",
          "proguard-rules-missing-classes.pro",
        )
      )
      buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "${project.isSnapshot()}")
    }
    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      isMinifyEnabled = false
      buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
    }
  }

  flavorDimensions.add(FlavorDimensions.FREE)
  productFlavors {
    register(ProductFlavors.FREE) {}
    register(ProductFlavors.NON_FREE) {}
  }

  project.configureSlimTests()
  project.configureBuildSigning()
}

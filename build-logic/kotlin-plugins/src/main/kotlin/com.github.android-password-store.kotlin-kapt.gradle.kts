/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

plugins {
  kotlin("android")
  kotlin("kapt")
}

afterEvaluate {
  extensions.configure<KaptExtension> {
    javacOptions {
      if (hasDaggerCompilerDependency()) {
        // https://dagger.dev/dev-guide/compiler-options#fastinit-mode
        option("-Adagger.fastInit=enabled")
        // Enable the better, experimental error messages
        // https://github.com/google/dagger/commit/0d2505a727b54f47b8677f42dd4fc5c1924e37f5
        option("-Adagger.experimentalDaggerErrorMessages=enabled")
        // Share test components for when we start leveraging Hilt for tests
        // https://github.com/google/dagger/releases/tag/dagger-2.34
        option("-Adagger.hilt.shareTestComponents=true")
        // KAPT nests errors causing real issues to be suppressed in CI logs
        option("-Xmaxerrs", 500)
        // Enables per-module validation for faster error detection
        // https://github.com/google/dagger/commit/325b516ac6a53d3fc973d247b5231fafda9870a2
        option("-Adagger.moduleBindingValidation=ERROR")
      }
    }
  }
}
// disable kapt tasks for unit tests
tasks.matching { it.name.startsWith("kapt") && it.name.endsWith("UnitTestKotlin") }.configureEach {
  enabled = false
}

fun Project.hasDaggerCompilerDependency(): Boolean {
  return configurations.any {
    it.dependencies.any { dependency -> dependency.name == "hilt-compiler" }
  }
}

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

@Suppress("Unused")
class KotlinKaptPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(Kapt3GradleSubplugin::class)
    project.afterEvaluate {
      project.extensions.configure<KaptExtension> {
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
            option("-Xmaxerrs", "500")
            // Enables per-module validation for faster error detection
            // https://github.com/google/dagger/commit/325b516ac6a53d3fc973d247b5231fafda9870a2
            option("-Adagger.moduleBindingValidation=ERROR")
          }
        }
      }
    }
    project.tasks
      .matching { it.name.startsWith("kapt") && it.name.endsWith("UnitTestKotlin") }
      .configureEach { enabled = false }
  }

  private fun Project.hasDaggerCompilerDependency(): Boolean {
    return configurations.any {
      it.dependencies.any { dependency -> dependency.name == "hilt-compiler" }
    }
  }
}

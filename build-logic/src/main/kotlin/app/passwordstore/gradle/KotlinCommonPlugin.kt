/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("Unused")
class KotlinCommonPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val isAppModule = project.pluginManager.hasPlugin("com.android.application")
    project.tasks.run {
      withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
      }
      withType<KotlinCompile>().configureEach task@{
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_11)
          allWarningsAsErrors.set(true)
          languageVersion.set(KotlinVersion.KOTLIN_1_8)
          freeCompilerArgs.addAll(ADDITIONAL_COMPILER_ARGS)
          if (!this@task.name.contains("test", ignoreCase = true) && !isAppModule) {
            freeCompilerArgs.add("-Xexplicit-api=strict")
          }
        }
      }
      withType<Test>().configureEach {
        maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
        testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
      }
    }
  }

  private companion object {
    private val ADDITIONAL_COMPILER_ARGS =
      listOf(
        "-opt-in=kotlin.RequiresOptIn",
        "-Xsuppress-version-warnings",
      )
  }
}

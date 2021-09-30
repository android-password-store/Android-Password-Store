/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class PasswordStorePlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.configureForAllProjects()

    if (project.isRoot) {
      project.configureForRootProject()
    }

    project.plugins.all {
      when (this) {
        is JavaPlugin, is JavaLibraryPlugin -> {
          project.tasks.withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:unchecked")
            options.isDeprecation = true
            options.isWarnings = true
          }
        }
        is LibraryPlugin -> {
          project.extensions.getByType<TestedExtension>().configureCommonAndroidOptions()
          project.configureExplicitApi()
          project.configureSlimTests()
        }
        is AppPlugin -> {
          project
            .extensions
            .getByType<BaseAppModuleExtension>()
            .configureAndroidApplicationOptions(project)
          project.extensions.getByType<BaseAppModuleExtension>().configureBuildSigning(project)
          project.extensions.getByType<TestedExtension>().configureCommonAndroidOptions()
          project.configureSlimTests()
        }
        is SigningPlugin -> {
          project.extensions.getByType<SigningExtension>().configureBuildSigning()
        }
        is KotlinPluginWrapper -> {
          project.configureExplicitApi()
        }
        is Kapt3GradleSubplugin -> {
          project.configureKapt()
        }
      }
    }
  }

  private fun Project.configureExplicitApi() {
    configure<KotlinProjectExtension> { explicitApi() }
    tasks.withType<KotlinCompile> {
      kotlinOptions { freeCompilerArgs = freeCompilerArgs + listOf("-Xexplicit-api=strict") }
    }
  }
}

private val Project.isRoot
  get() = this == this.rootProject

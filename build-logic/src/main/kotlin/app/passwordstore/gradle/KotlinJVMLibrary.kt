/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.KotlinCommonPlugin.Companion.JVM_TOOLCHAIN_ACTION
import app.passwordstore.gradle.LintConfig.configureLint
import com.android.build.api.dsl.Lint
import com.android.build.gradle.LintPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

@Suppress("Unused")
class KotlinJVMLibrary : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.run {
      apply(KotlinPluginWrapper::class)
      apply(LintPlugin::class)
      apply(KotlinCommonPlugin::class)
    }
    project.extensions.configure<Lint> { configureLint(project, isJVM = true) }
    project.extensions.getByType<JavaPluginExtension>().toolchain(JVM_TOOLCHAIN_ACTION)
    project.extensions.getByType<KotlinProjectExtension>().jvmToolchain(JVM_TOOLCHAIN_ACTION)
  }
}

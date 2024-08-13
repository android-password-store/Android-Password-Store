/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.KotlinCommonPlugin.Companion.JVM_TOOLCHAIN_ACTION
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

@Suppress("Unused")
class KotlinAndroidPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.run {
      apply(KotlinAndroidPluginWrapper::class)
      apply(KotlinCommonPlugin::class)
    }
    project.extensions.getByType<KotlinProjectExtension>().jvmToolchain(JVM_TOOLCHAIN_ACTION)
  }
}

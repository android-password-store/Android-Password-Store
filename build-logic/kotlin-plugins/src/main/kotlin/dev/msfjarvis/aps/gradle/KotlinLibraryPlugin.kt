/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("Unused")
class KotlinLibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(KotlinCommonPlugin::class)
    project.tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions {
        if (!name.contains("test", ignoreCase = true)) {
          freeCompilerArgs = freeCompilerArgs + listOf("-Xexplicit-api=strict")
        }
      }
    }
  }
}

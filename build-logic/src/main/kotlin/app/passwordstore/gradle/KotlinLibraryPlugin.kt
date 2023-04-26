/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

@Suppress("Unused")
class KotlinLibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(KotlinCommonPlugin::class)
  }
}

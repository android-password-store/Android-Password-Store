/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

@Suppress("Unused")
class LibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.run {
      apply(LibraryPlugin::class)
      apply(KotlinCommonPlugin::class)
    }
    AndroidCommon.configure(project)
  }
}

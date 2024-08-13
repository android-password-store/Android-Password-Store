/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.gradle

import com.github.zafarkhaja.semver.Version
import kotlin.jvm.optionals.getOrNull
import nl.littlerobots.vcu.plugin.VersionCatalogUpdateExtension
import nl.littlerobots.vcu.plugin.VersionCatalogUpdatePlugin
import nl.littlerobots.vcu.plugin.versionSelector
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

@Suppress("Unused")
class DependencyUpdatesPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(VersionCatalogUpdatePlugin::class)
    project.extensions.configure<VersionCatalogUpdateExtension> {
      keep.keepUnusedLibraries.set(true)
      versionSelector {
        val currentVersion = Version.tryParse(it.currentVersion).getOrNull()
        val newVersion = Version.tryParse(it.candidate.version).getOrNull()
        if (currentVersion == null || newVersion == null) {
          false
        } else {
          newVersion.isHigherThan(currentVersion)
        }
      }
    }
  }
}

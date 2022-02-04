/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package versioning

import com.android.build.gradle.internal.plugins.AppPlugin
import com.vdurmont.semver4j.Semver
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * A Gradle [Plugin] that takes a [Project] with the [AppPlugin] applied and dynamically sets the
 * versionCode and versionName properties based on values read from a [VERSIONING_PROP_FILE] file in
 * the [Project.getBuildDir] directory. It also adds Gradle tasks to bump the major, minor, and
 * patch versions along with one to prepare the next snapshot.
 */
@Suppress("UnstableApiUsage", "NAME_SHADOWING", "Unused")
class VersioningPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project) {
      val appPlugin =
        requireNotNull(plugins.findPlugin(AppPlugin::class.java)) {
          "Plugin 'com.android.application' must be applied to use this plugin"
        }
      val propFile = layout.projectDirectory.file(VERSIONING_PROP_FILE)
      require(propFile.asFile.exists()) {
        "A 'version.properties' file must exist in the project subdirectory to use this plugin"
      }
      val contents = providers.fileContents(propFile).asText
      val versionProps = Properties().also { it.load(contents.get().byteInputStream()) }
      val versionName =
        requireNotNull(versionProps.getProperty(VERSIONING_PROP_VERSION_NAME)) {
          "version.properties must contain a '$VERSIONING_PROP_VERSION_NAME' property"
        }
      val versionCode =
        requireNotNull(versionProps.getProperty(VERSIONING_PROP_VERSION_CODE).toInt()) {
          "version.properties must contain a '$VERSIONING_PROP_VERSION_CODE' property"
        }
      appPlugin.extension.defaultConfig.versionName = versionName
      appPlugin.extension.defaultConfig.versionCode = versionCode
      afterEvaluate {
        val version = Semver(versionName)
        tasks.register<VersioningTask>("clearPreRelease") {
          semverString.set(version.withClearedSuffix().toString())
          propertyFile.set(propFile)
        }
        tasks.register<VersioningTask>("bumpMajor") {
          semverString.set(version.withIncMajor().withClearedSuffix().toString())
          propertyFile.set(propFile)
        }
        tasks.register<VersioningTask>("bumpMinor") {
          semverString.set(version.withIncMinor().withClearedSuffix().toString())
          propertyFile.set(propFile)
        }
        tasks.register<VersioningTask>("bumpPatch") {
          semverString.set(version.withIncPatch().withClearedSuffix().toString())
          propertyFile.set(propFile)
        }
        tasks.register<VersioningTask>("bumpSnapshot") {
          semverString.set(version.withIncMinor().withSuffix("SNAPSHOT").toString())
          propertyFile.set(propFile)
        }
      }
    }
  }
}

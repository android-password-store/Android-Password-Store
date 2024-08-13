/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.versioning

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.plugins.AppPlugin
import com.github.zafarkhaja.semver.Version
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * A Gradle [Plugin] that takes a [Project] with the [AppPlugin] applied and dynamically sets the
 * versionCode and versionName properties based on values read from a [VERSIONING_PROP_FILE] file in
 * the [Project.getBuildDir] directory. It also adds Gradle tasks to bump the major, minor, and
 * patch versions along with one to prepare the next snapshot.
 */
@Suppress("Unused")
class VersioningPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project) {
      val androidAppPluginApplied = AtomicBoolean(false)
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
      project.plugins.withType<AppPlugin> {
        androidAppPluginApplied.set(true)
        extensions.configure<ApplicationAndroidComponentsExtension> {
          onVariants { variant ->
            val mainOutput =
              variant.outputs.single {
                it.outputType == VariantOutputConfiguration.OutputType.SINGLE
              }
            mainOutput.versionName.set(versionName)
            mainOutput.versionCode.set(versionCode)
          }
        }
      }
      val version = Version.parse(versionName)
      tasks.register<VersioningTask>("clearPreRelease") {
        description = "Remove the pre-release suffix from the version"
        semverString.set(version.toStableVersion().toString())
        propertyFile.set(propFile)
      }
      tasks.register<VersioningTask>("bumpMajor") {
        description = "Increment the major version"
        semverString.set(version.nextMajorVersion().toString())
        propertyFile.set(propFile)
      }
      tasks.register<VersioningTask>("bumpMinor") {
        description = "Increment the minor version"
        semverString.set(version.nextMinorVersion().toString())
        propertyFile.set(propFile)
      }
      tasks.register<VersioningTask>("bumpPatch") {
        description = "Increment the patch version"
        semverString.set(version.nextPatchVersion().toString())
        propertyFile.set(propFile)
      }
      tasks.register<VersioningTask>("bumpSnapshot") {
        description = "Increment the minor version and add the `SNAPSHOT` suffix"
        semverString.set(version.nextMinorVersion("SNAPSHOT").toString())
        propertyFile.set(propFile)
      }
      afterEvaluate {
        check(androidAppPluginApplied.get()) {
          "Plugin 'com.android.application' must be applied to ${project.displayName} to use the Versioning Plugin"
        }
      }
    }
  }
}

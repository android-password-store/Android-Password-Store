/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.artifacts.CollectApksTask
import app.passwordstore.gradle.artifacts.CollectBundleTask
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

@Suppress("Unused")
class RenameArtifactsPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.withPlugin("com.android.application") {
      project.extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
          project.tasks.register<CollectApksTask>(
            "collect${variant.name.replaceFirstChar { it.uppercase() }}Apks"
          ) {
            variantName.set(variant.name)
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
          }
          project.tasks.register<CollectBundleTask>(
            "collect${variant.name.replaceFirstChar { it.uppercase() }}Bundle"
          ) {
            val mainOutput =
              variant.outputs.single {
                it.outputType == VariantOutputConfiguration.OutputType.SINGLE
              }
            variantName.set(variant.name)
            versionName.set(mainOutput.versionName)
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
          }
        }
      }
    }
  }
}

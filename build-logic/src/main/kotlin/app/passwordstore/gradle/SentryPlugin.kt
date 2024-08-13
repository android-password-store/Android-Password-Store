/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.flavors.FlavorDimensions
import app.passwordstore.gradle.flavors.ProductFlavors
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.sentry.android.gradle.SentryPlugin
import io.sentry.android.gradle.extensions.SentryPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

@Suppress("Unused")
class SentryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.withPlugin("com.android.application") {
      project.extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withFlavor(FlavorDimensions.FREE to ProductFlavors.NON_FREE)) {
          variant ->
          val sentryDsn = project.providers.environmentVariable(SENTRY_DSN_PROPERTY).orElse("")
          variant.manifestPlaceholders.put("sentryDsn", sentryDsn.get())
        }
      }
      project.plugins.apply(SentryPlugin::class)
      project.extensions.configure<SentryPluginExtension> {
        includeProguardMapping.set(
          project.providers.gradleProperty(SENTRY_UPLOAD_MAPPINGS_PROPERTY).isPresent
        )
        autoUploadProguardMapping.set(
          project.providers.gradleProperty(SENTRY_UPLOAD_MAPPINGS_PROPERTY).isPresent
        )
        ignoredBuildTypes.set(setOf("debug"))
        ignoredFlavors.set(setOf(ProductFlavors.FREE))
        tracingInstrumentation { enabled.set(false) }
        autoInstallation.enabled.set(false)
        includeDependenciesReport.set(false)
      }
    }
  }

  private companion object {

    private const val SENTRY_DSN_PROPERTY = "SENTRY_DSN"
    private const val SENTRY_UPLOAD_MAPPINGS_PROPERTY = "sentryUploadMappings"
  }
}

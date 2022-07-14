/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.BinaryCompatibilityValidatorPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

@Suppress("Unused")
class BinaryCompatibilityPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(BinaryCompatibilityValidatorPlugin::class)
    project.extensions.getByType<ApiValidationExtension>().ignoredProjects =
      mutableSetOf(
        "app",
        "coroutine-utils",
        "coroutine-utils-testing",
        "crypto-common",
        "crypto-pgpainless",
        "format-common",
        "diceware",
        "random",
        "sentry-stub",
        "ui-compose",
      )
  }
}

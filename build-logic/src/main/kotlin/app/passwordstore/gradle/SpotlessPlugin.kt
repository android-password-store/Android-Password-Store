/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

@Suppress("Unused")
class SpotlessPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(SpotlessPlugin::class)
    project.extensions.getByType<SpotlessExtension>().run {
      kotlin {
        ktfmt(KTFMT_VERSION).googleStyle()
        target("**/*.kt")
        targetExclude("**/build/")
      }
      kotlinGradle {
        ktfmt(KTFMT_VERSION).googleStyle()
        target("**/*.kts")
        targetExclude("**/build/")
      }
      format("xml") {
        target("**/*.xml")
        targetExclude("**/build/", ".idea/")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
      }
    }
  }

  private companion object {
    private const val KTFMT_VERSION = "0.52"
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

fun Project.configureSpotless() {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    kotlin {
      ktfmt().googleStyle()
      target("**/*.kt")
      targetExclude("**/build/")
    }
    kotlinGradle {
      ktfmt().googleStyle()
      target("**/*.kts")
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

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.psl

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/** Gradle plugin to update the public suffix list used by the `autofill-parser` library. */
@Suppress("Unused")
class PublicSuffixListPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.tasks.register<PSLUpdateTask>("updatePSL") {
      outputFile.set(project.layout.projectDirectory.file("src/main/assets/publicsuffixes"))
    }
  }
}

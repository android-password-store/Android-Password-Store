/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.tasks.GitHooks
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

@Suppress("Unused")
class GitHooksPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.tasks.register<GitHooks>("installGitHooks") {
      val projectDirectory = project.layout.projectDirectory
      hookSource.set(projectDirectory.file("scripts/pre-push-hook.sh"))
      hookOutput.set(projectDirectory.file(".git/hooks/pre-push"))
    }
  }
}

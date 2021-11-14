/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import tasks.GitHooks

tasks.register<GitHooks>("installGitHooks") {
  val projectDirectory = layout.projectDirectory
  hookScript.set(projectDirectory.file("scripts/pre-push-hook.sh").asFile.readText())
  hookOutput.set(projectDirectory.file(".git/hooks/pre-push").asFile)
}

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.git.operation

import androidx.appcompat.app.AppCompatActivity
import org.eclipse.jgit.api.GitCommand

class SyncOperation(callingActivity: AppCompatActivity, rebase: Boolean) :
  GitOperation(callingActivity) {

  override val commands: Array<GitCommand<out Any>> =
    arrayOf(
      // Stage all files
      git.add().addFilepattern("."),
      // Populate the changed files count
      git.status(),
      // Commit everything! If needed, obviously.
      git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
      // Pull and rebase on top of the remote branch
      git.pull().setRebase(rebase).setRemote("origin"),
      // Push it all back
      git.push().setPushAll().setRemote("origin"),
    )
}

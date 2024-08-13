/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.git.operation

import androidx.appcompat.app.AppCompatActivity
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.util.extensions.unsafeLazy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.RepositoryState

class BreakOutOfDetached(callingActivity: AppCompatActivity) : GitOperation(callingActivity) {

  private val merging = repository.repositoryState == RepositoryState.MERGING
  private val localBranch = PasswordRepository.getCurrentBranch()
  private val resetCommands =
    arrayOf(
      // git checkout -b conflict-branch
      git
        .checkout()
        .setCreateBranch(true)
        .setName("conflicting-$localBranch-${System.currentTimeMillis()}"),
      // push the changes
      git.push().setRemote("origin"),
      // switch back to ${gitBranch}
      git.checkout().setName(localBranch),
    )

  override val commands: Array<GitCommand<out Any>> by unsafeLazy {
    if (merging) {
      // We need to run some non-command operations first
      repository.writeMergeCommitMsg(null)
      repository.writeMergeHeads(null)
      arrayOf(
        // reset hard back to our local HEAD
        git.reset().setMode(ResetCommand.ResetType.HARD),
        *resetCommands,
      )
    } else {
      arrayOf(
        // abort the rebase
        git.rebase().setOperation(RebaseCommand.Operation.ABORT),
        *resetCommands,
      )
    }
  }

  override fun preExecute() =
    if (!git.repository.repositoryState.isRebasing && !merging) {
      MaterialAlertDialogBuilder(callingActivity)
        .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
        .setMessage(
          callingActivity.resources.getString(R.string.git_break_out_of_detached_unneeded)
        )
        .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
          callingActivity.finish()
        }
        .show()
      false
    } else {
      true
    }
}

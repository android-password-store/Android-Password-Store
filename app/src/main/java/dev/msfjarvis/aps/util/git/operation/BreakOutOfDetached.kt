/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.operation

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.git.sshj.ContinuationContainerActivity
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.RepositoryState

class BreakOutOfDetached(callingActivity: ContinuationContainerActivity) : GitOperation(callingActivity) {

    private val merging = repository.repositoryState == RepositoryState.MERGING
    private val resetCommands = arrayOf(
        // git checkout -b conflict-branch
        git.checkout().setCreateBranch(true).setName("conflicting-$remoteBranch-${System.currentTimeMillis()}"),
        // push the changes
        git.push().setRemote("origin"),
        // switch back to ${gitBranch}
        git.checkout().setName(remoteBranch),
    )

    override val commands by lazy(LazyThreadSafetyMode.NONE) {
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

    override fun preExecute() = if (!git.repository.repositoryState.isRebasing && !merging) {
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
            .setMessage(callingActivity.resources.getString(R.string.git_break_out_of_detached_unneeded))
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                callingActivity.finish()
            }.show()
        false
    } else {
        true
    }
}

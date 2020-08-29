/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.RebaseCommand

class BreakOutOfDetached(callingActivity: AppCompatActivity) : GitOperation(callingActivity) {

    private val branchName = "conflicting-$remoteBranch-${System.currentTimeMillis()}"

    override val commands = arrayOf(
        // abort the rebase
        git.rebase().setOperation(RebaseCommand.Operation.ABORT),
        // git checkout -b conflict-branch
        git.checkout().setCreateBranch(true).setName(branchName),
        // push the changes
        git.push().setRemote("origin"),
        // switch back to ${gitBranch}
        git.checkout().setName(remoteBranch),
    )

    override fun preExecute() = if (!git.repository.repositoryState.isRebasing) {
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

    override fun onSuccess() {
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
            .setMessage(callingActivity.resources.getString(
                R.string.git_break_out_of_detached_success,
                remoteBranch,
                branchName,
            ))
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                callingActivity.finish()
            }.show()
    }
}

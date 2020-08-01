/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.ErrorMessages
import com.zeapo.pwdstore.git.GitCommandExecutor
import com.zeapo.pwdstore.utils.PreferenceKeys
import java.io.File
import org.eclipse.jgit.api.RebaseCommand

class BreakOutOfDetached(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    private val gitBranch = PreferenceManager
        .getDefaultSharedPreferences(callingActivity.applicationContext)
        .getString(PreferenceKeys.GIT_BRANCH_NAME, "master")
    private val branchName = "conflicting-$gitBranch-${System.currentTimeMillis()}"

    override val commands = arrayOf(
        // abort the rebase
        git.rebase().setOperation(RebaseCommand.Operation.ABORT),
        // git checkout -b conflict-branch
        git.checkout().setCreateBranch(true).setName(branchName),
        // push the changes
        git.push().setRemote("origin"),
        // switch back to ${gitBranch}
        git.checkout().setName(gitBranch),
    )

    override suspend fun execute() {
        if (!git.repository.repositoryState.isRebasing) {
            MaterialAlertDialogBuilder(callingActivity)
                .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
                .setMessage("The repository is not rebasing, no need to push to another branch")
                .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                    callingActivity.finish()
                }.show()
            return
        }
        GitCommandExecutor(callingActivity, this).execute()
    }

    override fun onError(err: Exception) {
        super.onError(err)
        val error = ErrorMessages[err]
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage(error)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                callingActivity.finish()
            }.show()
    }

    override fun onSuccess() {
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
            .setMessage("There was a conflict when trying to rebase. " +
                "Your local $gitBranch branch was pushed to another branch named conflicting-$gitBranch-....\n" +
                "Use this branch to resolve conflict on your computer")
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                callingActivity.finish()
            }.show()
    }
}

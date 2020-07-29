/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PreferenceKeys
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.RebaseCommand

class BreakOutOfDetached(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    private lateinit var commands: List<GitCommand<out Any>>
    private val gitBranch = PreferenceManager
        .getDefaultSharedPreferences(callingActivity.applicationContext)
        .getString(PreferenceKeys.GIT_BRANCH_NAME, "master")

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommands(): BreakOutOfDetached {
        val git = Git(repository)
        val branchName = "conflicting-$gitBranch-${System.currentTimeMillis()}"

        this.commands = listOf(
            // abort the rebase
            git.rebase().setOperation(RebaseCommand.Operation.ABORT),
            // git checkout -b conflict-branch
            git.checkout().setCreateBranch(true).setName(branchName),
            // push the changes
            git.push().setRemote("origin"),
            // switch back to ${gitBranch}
            git.checkout().setName(gitBranch)
        )
        return this
    }

    override fun execute() {
        val git = Git(repository)
        if (!git.repository.repositoryState.isRebasing) {
            MaterialAlertDialogBuilder(callingActivity)
                .setTitle(callingActivity.resources.getString(R.string.git_abort_and_push_title))
                .setMessage("The repository is not rebasing, no need to push to another branch")
                .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                    callingActivity.finish()
                }.show()
            return
        }

        if (this.provider != null) {
            // set the credentials for push command
            this.commands.forEach { cmd ->
                if (cmd is PushCommand) {
                    cmd.setCredentialsProvider(this.provider)
                }
            }
        }
        GitAsyncTask(callingActivity, this, null)
            .execute(*this.commands.toTypedArray())
    }

    override fun onError(err: Exception) {
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occurred when checking out another branch operation ${err.message}")
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

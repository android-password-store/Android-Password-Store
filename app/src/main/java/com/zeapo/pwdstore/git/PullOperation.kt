/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import java.io.File
import org.eclipse.jgit.api.FetchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.PullCommand

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class PullOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    private var pullCommand: PullCommand? = null
    private var fetchCommand: FetchCommand? = null
    private var mergeCommand: MergeCommand? = null

    /**
     * Sets the commands required to do a git pull on the repository. The [rebase] parameter decides
     * whether the pull will be of type `git pull origin --rebase` or `git pull origin --no-rebase`.
     *
     * @return An instance of [PullOperation]
     */
    fun setCommand(rebase: Boolean): PullOperation {
        val git = Git(repository)
        if (rebase) {
            pullCommand = git
                .pull()
                .setRebase(true)
                .setRemote("origin")
        } else {
            fetchCommand = git.fetch().setRemote("origin")
            mergeCommand = git.merge().include(repository!!.resolve("origin/master"))
        }
        return this
    }

    override fun execute() {
        pullCommand?.setCredentialsProvider(this.provider)
        fetchCommand?.setCredentialsProvider(this.provider)
        val task = GitAsyncTask(callingActivity, this, Intent())
        if (pullCommand != null) {
            task.execute(this.pullCommand)
        } else {
            task.execute(this.fetchCommand, this.mergeCommand)
        }
    }

    override fun onError(err: Exception) {
        super.onError(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occurred during the pull operation, " +
                callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                err.message +
                "\nPlease check the FAQ for possible reasons why this error might occur.")
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> callingActivity.finish() }
            .show()
    }
}

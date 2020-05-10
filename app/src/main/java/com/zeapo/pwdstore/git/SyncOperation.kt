/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.app.Activity
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.AddCommand
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.StatusCommand
import java.io.File

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class SyncOperation(fileDir: File, callingActivity: Activity) : GitOperation(fileDir, callingActivity) {

    private var addCommand: AddCommand? = null
    private var statusCommand: StatusCommand? = null
    private var commitCommand: CommitCommand? = null
    private var pullCommand: PullCommand? = null
    private var pushCommand: PushCommand? = null

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommands(): SyncOperation {
        val git = Git(repository)
        this.addCommand = git.add().addFilepattern(".")
        this.statusCommand = git.status()
        this.commitCommand = git.commit().setAll(true).setMessage("[Android Password Store] Sync")
        this.pullCommand = git.pull().setRebase(true).setRemote("origin")
        this.pushCommand = git.push().setPushAll().setRemote("origin")
        return this
    }

    override fun execute() {
        if (this.provider != null) {
            this.pullCommand?.setCredentialsProvider(this.provider)
            this.pushCommand?.setCredentialsProvider(this.provider)
        }
        GitAsyncTask(callingActivity, false, this, Intent()).execute(this.addCommand, this.statusCommand, this.commitCommand, this.pullCommand, this.pushCommand)
    }

    override fun onError(errorMessage: String) {
        super.onError(errorMessage)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occured during the sync operation, " +
                "\nPlease check the FAQ for possible reasons why this error might occur." +
                callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                errorMessage)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> callingActivity.finish() }
            .show()
    }
}

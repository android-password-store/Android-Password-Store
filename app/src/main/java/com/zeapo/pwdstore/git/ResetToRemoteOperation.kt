/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.git

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.AddCommand
import org.eclipse.jgit.api.FetchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import java.io.File

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class ResetToRemoteOperation(fileDir: File, callingActivity: Activity) : GitOperation(fileDir, callingActivity) {
    private var addCommand: AddCommand? = null
    private var fetchCommand: FetchCommand? = null
    private var resetCommand: ResetCommand? = null

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommands(): ResetToRemoteOperation {
        val git = Git(repository)
        this.addCommand = git.add().addFilepattern(".")
        this.fetchCommand = git.fetch().setRemote("origin")
        this.resetCommand = git.reset().setRef("origin/master").setMode(ResetCommand.ResetType.HARD)
        return this
    }

    override fun execute() {
        this.fetchCommand?.setCredentialsProvider(this.provider)
        GitAsyncTask(callingActivity, true, false, this)
                .execute(this.addCommand, this.fetchCommand, this.resetCommand)
    }

    override fun onError(errorMessage: String) {
        MaterialAlertDialogBuilder(callingActivity)
                .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
                .setMessage("Error occured during the sync operation, " +
                        "\nPlease check the FAQ for possible reasons why this error might occur." +
                        callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                        errorMessage)
                .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> }
                .show()
    }
}

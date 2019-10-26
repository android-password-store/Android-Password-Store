/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullCommand
import java.io.File

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class PullOperation(fileDir: File, callingActivity: Activity) : GitOperation(fileDir, callingActivity) {

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommand(): PullOperation {
        this.command = Git(repository)
                .pull()
                .setRebase(true)
                .setRemote("origin")
        return this
    }

    override fun execute() {
        (this.command as? PullCommand)?.setCredentialsProvider(this.provider)
        GitAsyncTask(callingActivity, true, false, this).execute(this.command)
    }

    override fun onError(errorMessage: String) {
        super.onError(errorMessage)
        MaterialAlertDialogBuilder(callingActivity)
                .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
                .setMessage("Error occured during the pull operation, " +
                        callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                        errorMessage +
                        "\nPlease check the FAQ for possible reasons why this error might occur.")
                .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> callingActivity.finish() }
                .show()
    }
}

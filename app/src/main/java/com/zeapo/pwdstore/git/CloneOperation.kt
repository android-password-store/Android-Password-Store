/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import java.io.File

/**
 * Creates a new clone operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class CloneOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    /**
     * Sets the command using the repository uri
     *
     * @param uri the uri of the repository
     * @return the current object
     */
    fun setCommand(uri: String): CloneOperation {
        this.command = Git.cloneRepository()
            .setCloneAllBranches(true)
            .setDirectory(repository?.workTree)
            .setURI(uri)
        return this
    }

    override fun execute() {
        (this.command as? CloneCommand)?.setCredentialsProvider(this.provider)
        GitAsyncTask(callingActivity, this, Intent()).execute(this.command)
    }

    override fun onError(err: Exception) {
        super.onError(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occurred during the clone operation, " +
                callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                err.message +
                "\nPlease check the FAQ for possible reasons why this error might occur.")
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> }
            .show()
    }
}

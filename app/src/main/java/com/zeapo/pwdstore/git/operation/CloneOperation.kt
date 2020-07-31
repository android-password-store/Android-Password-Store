/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.GitAsyncTask
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand

/**
 * Creates a new clone operation
 *
 * @param fileDir the git working tree directory
 * @param uri URL to clone the repository from
 * @param callingActivity the calling activity
 */
class CloneOperation(fileDir: File, uri: String, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    override val commands: Array<GitCommand<out Any>> = arrayOf(
        Git.cloneRepository().setCloneAllBranches(true).setDirectory(repository?.workTree).setURI(uri),
    )

    override fun execute() {
        setCredentialProvider()
        GitAsyncTask(callingActivity, this, Intent()).execute(*commands)
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

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.ErrorMessages
import com.zeapo.pwdstore.git.GitCommandExecutor
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
        Git.cloneRepository().setBranch(remoteBranch).setDirectory(repository?.workTree).setURI(uri),
    )

    override suspend fun execute() {
        GitCommandExecutor(callingActivity, this).execute()
    }

    override fun onError(err: Exception) {
        super.onError(err)
        val error = ErrorMessages[err]
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage(error)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> }
            .show()
    }
}

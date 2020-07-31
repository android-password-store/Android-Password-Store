/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.GitCommandExecutor
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class PullOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    override val commands: Array<GitCommand<out Any>> = arrayOf(
        Git(repository).pull().setRebase(true).setRemote("origin"),
    )

    override suspend fun execute() {
        GitCommandExecutor(callingActivity, this).execute()
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

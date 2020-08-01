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
import org.eclipse.jgit.api.ResetCommand

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class ResetToRemoteOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    override val commands = arrayOf(
        git.add().addFilepattern("."),
        git.fetch().setRemote("origin"),
        git.reset().setRef("origin/$remoteBranch").setMode(ResetCommand.ResetType.HARD),
        git.branchCreate().setName(remoteBranch).setForce(true),
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

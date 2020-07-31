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

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class SyncOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    override val commands = arrayOf(
        git.add().addFilepattern("."),
        git.status(),
        git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
        git.pull().setRebase(true).setRemote("origin"),
        git.push().setPushAll().setRemote("origin"),
    )

    override fun execute() {
        setCredentialProvider()
        GitAsyncTask(callingActivity, this, Intent()).execute(*commands)
    }

    override fun onError(err: Exception) {
        super.onError(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occurred during the sync operation, " +
                "\nPlease check the FAQ for possible reasons why this error might occur." +
                callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                err)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> callingActivity.finish() }
            .show()
    }
}

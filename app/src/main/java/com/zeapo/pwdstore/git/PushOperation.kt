/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PushCommand
import java.io.File

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class PushOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommand(): PushOperation {
        this.command = Git(repository)
            .push()
            .setPushAll()
            .setRemote("origin")
        return this
    }

    override fun execute() {
        (this.command as? PushCommand)?.setCredentialsProvider(this.provider)
        GitAsyncTask(callingActivity, this, Intent()).execute(this.command)
    }

    override fun onError(err: Exception) {
        // TODO handle the "Nothing to push" case
        super.onError(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage(callingActivity.getString(R.string.jgit_error_push_dialog_text) + err.message)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> callingActivity.finish() }
            .show()
    }
}

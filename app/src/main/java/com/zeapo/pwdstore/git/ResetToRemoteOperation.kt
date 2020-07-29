/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PreferenceKeys
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportCommand

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
class ResetToRemoteOperation(fileDir: File, callingActivity: AppCompatActivity) : GitOperation(fileDir, callingActivity) {

    private lateinit var commands: List<GitCommand<out Any>>

    /**
     * Sets the command
     *
     * @return the current object
     */
    fun setCommands(): ResetToRemoteOperation {
        val remoteBranch = PreferenceManager
            .getDefaultSharedPreferences(callingActivity.applicationContext)
            .getString(PreferenceKeys.GIT_BRANCH_NAME, "master")
        val git = Git(repository)
        val cmds = arrayListOf(
            git.add().addFilepattern("."),
            git.fetch().setRemote("origin"),
            git.reset().setRef("origin/$remoteBranch").setMode(ResetCommand.ResetType.HARD)
        )
        if (git.branchList().call().none { it.name == remoteBranch }) {
            cmds.add(
                git.branchCreate().setName(remoteBranch).setForce(true)
            )
        }
        commands = cmds
        return this
    }

    override fun execute() {
        commands.filterIsInstance<TransportCommand<*, *>>().map { it.setCredentialsProvider(provider) }
        GitAsyncTask(callingActivity, this, Intent()).execute(*commands.toTypedArray())
    }

    override fun onError(err: Exception) {
        super.onError(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage("Error occurred during the sync operation, " +
                "\nPlease check the FAQ for possible reasons why this error might occur." +
                callingActivity.resources.getString(R.string.jgit_error_dialog_text) +
                err)
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ -> }
            .show()
    }
}

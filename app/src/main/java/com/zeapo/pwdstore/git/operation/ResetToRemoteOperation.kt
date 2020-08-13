/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
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
        // Stage all files
        git.add().addFilepattern("."),
        // Fetch everything from the origin remote
        git.fetch().setRemote("origin"),
        // Do a hard reset to the remote branch. Equivalent to git reset --hard origin/$remoteBranch
        git.reset().setRef("origin/$remoteBranch").setMode(ResetCommand.ResetType.HARD),
        // Force-create $remoteBranch if it doesn't exist. This covers the case where you switched
        // branches from 'master' to anything else.
        git.branchCreate().setName(remoteBranch).setForce(true),
    )

    override suspend fun execute() {
        GitCommandExecutor(callingActivity, this).execute()
    }
}

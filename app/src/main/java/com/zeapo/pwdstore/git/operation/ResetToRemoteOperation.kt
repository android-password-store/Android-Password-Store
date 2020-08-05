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
        git.add().addFilepattern("."),
        git.fetch().setRemote("origin"),
        git.reset().setRef("origin/$remoteBranch").setMode(ResetCommand.ResetType.HARD),
        git.branchCreate().setName(remoteBranch).setForce(true),
    )

    override suspend fun execute() {
        GitCommandExecutor(callingActivity, this).execute()
    }
}

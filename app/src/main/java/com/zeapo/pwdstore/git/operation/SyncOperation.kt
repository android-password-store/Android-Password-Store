/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import com.zeapo.pwdstore.git.GitCommandExecutor
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
        git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
        git.pull().setRebase(true).setRemote("origin"),
        git.push().setPushAll().setRemote("origin"),
    )

    override suspend fun execute() {
        GitCommandExecutor(callingActivity, this).execute()
    }
}

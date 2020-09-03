/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity

class SyncOperation(callingActivity: AppCompatActivity) : GitOperation(callingActivity) {

    override val commands = arrayOf(
        // Stage all files
        git.add().addFilepattern("."),
        // Populate the changed files count
        git.status(),
        // Commit everything! If needed, obviously.
        git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
        // Pull and rebase on top of the remote branch
        git.pull().setRebase(true).setRemote("origin"),
        // Push it all back
        git.push().setPushAll().setRemote("origin"),
    )
}

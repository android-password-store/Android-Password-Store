/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.operation

import dev.msfjarvis.aps.util.git.sshj.ContinuationContainerActivity

class SyncOperation(
    callingActivity: ContinuationContainerActivity,
    rebase: Boolean,
) : GitOperation(callingActivity) {

    override val commands = arrayOf(
        // Stage all files
        git.add().addFilepattern("."),
        // Populate the changed files count
        git.status(),
        // Commit everything! If needed, obviously.
        git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
        // Pull and rebase on top of the remote branch
        git.pull().setRebase(rebase).setRemote("origin"),
        // Push it all back
        git.push().setPushAll().setRemote("origin"),
    )
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import com.zeapo.pwdstore.git.sshj.ContinuationContainerActivity
import org.eclipse.jgit.api.GitCommand

class PullOperation(callingActivity: ContinuationContainerActivity) : GitOperation(callingActivity) {

    /**
     * The story of why the pull operation is committing files goes like this: Once upon a time when
     * the world was burning and Blade Runner 2049 was real life (in the worst way), we were made
     * aware that Bitbucket is actually bad, and disables a neat OpenSSH feature called multiplexing.
     * So now, rather than being able to do a [SyncOperation], we'd have to first do a [PullOperation]
     * and then a [PushOperation]. To make the behavior identical despite this suboptimal situation,
     * we opted to replicate [SyncOperation]'s committing flow within [PullOperation], almost exactly
     * replicating [SyncOperation] but leaving the pushing part to [PushOperation].
     */
    override val commands: Array<GitCommand<out Any>> = arrayOf(
        // Stage all files
        git.add().addFilepattern("."),
        // Populate the changed files count
        git.status(),
        // Commit everything! If needed, obviously.
        git.commit().setAll(true).setMessage("[Android Password Store] Sync"),
        // Pull and rebase on top of the remote branch
        git.pull().setRebase(true).setRemote("origin"),
    )
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand

/**
 * Creates a new clone operation
 *
 * @param uri URL to clone the repository from
 * @param callingActivity the calling activity
 */
class CloneOperation(callingActivity: AppCompatActivity, uri: String) : GitOperation(callingActivity) {

    override val commands: Array<GitCommand<out Any>> = arrayOf(
        Git.cloneRepository().setBranch(remoteBranch).setDirectory(repository.workTree).setURI(uri),
    )
}

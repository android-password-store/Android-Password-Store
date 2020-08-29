/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import org.eclipse.jgit.api.GitCommand

class PullOperation(callingActivity: AppCompatActivity) : GitOperation(callingActivity) {

    override val commands: Array<GitCommand<out Any>> = arrayOf(
        git.pull().setRebase(true).setRemote("origin"),
    )

    override fun onSuccess() {
        callingActivity.finish()
    }
}

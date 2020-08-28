/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import androidx.appcompat.app.AppCompatActivity
import org.eclipse.jgit.api.GitCommand

class PushOperation(callingActivity: AppCompatActivity) : GitOperation(callingActivity) {

    override val commands: Array<GitCommand<out Any>> = arrayOf(
        git.push().setPushAll().setRemote("origin"),
    )
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.e
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.operation.BreakOutOfDetached
import com.zeapo.pwdstore.git.operation.CloneOperation
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.git.operation.PullOperation
import com.zeapo.pwdstore.git.operation.PushOperation
import com.zeapo.pwdstore.git.operation.ResetToRemoteOperation
import com.zeapo.pwdstore.git.operation.SyncOperation
import com.zeapo.pwdstore.utils.PasswordRepository

/**
 * Abstract AppCompatActivity that holds some information that is commonly shared across git-related
 * tasks and makes sense to be held here.
 */
abstract class BaseGitActivity : AppCompatActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Attempt to launch the requested Git operation.
     * @param operation The type of git operation to launch
     */
    suspend fun launchGitOperation(operation: Int) {
        if (GitSettings.url == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        try {
            val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory())
            val op = when (operation) {
                REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> CloneOperation(localDir, GitSettings.url!!, this)
                REQUEST_PULL -> PullOperation(localDir, this)
                REQUEST_PUSH -> PushOperation(localDir, this)
                REQUEST_SYNC -> SyncOperation(localDir, this)
                BREAK_OUT_OF_DETACHED -> BreakOutOfDetached(localDir, this)
                REQUEST_RESET -> ResetToRemoteOperation(localDir, this)
                else -> {
                    tag(TAG).e { "Operation not recognized : $operation" }
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }
            op.executeAfterAuthentication(GitSettings.authMode)
        } catch (e: Exception) {
            e.printStackTrace()
            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }
    }

    companion object {

        const val REQUEST_ARG_OP = "OPERATION"
        const val REQUEST_PULL = 101
        const val REQUEST_PUSH = 102
        const val REQUEST_CLONE = 103
        const val REQUEST_SYNC = 104
        const val BREAK_OUT_OF_DETACHED = 105
        const val REQUEST_RESET = 106
        const val TAG = "AbstractGitActivity"
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.operation.BreakOutOfDetached
import com.zeapo.pwdstore.git.operation.CloneOperation
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.git.operation.PullOperation
import com.zeapo.pwdstore.git.operation.PushOperation
import com.zeapo.pwdstore.git.operation.ResetToRemoteOperation
import com.zeapo.pwdstore.git.operation.SyncOperation
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.isExplicitlyUserInitiatedError
import com.zeapo.pwdstore.utils.sharedPrefs

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
    suspend fun launchGitOperation(operation: Int): Result<Unit> {
        if (GitSettings.url == null) {
            return Result.failure(IllegalStateException("Git url is not set!"))
        }
        val op = when (operation) {
            REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> CloneOperation(this, GitSettings.url!!)
            REQUEST_PULL -> PullOperation(this)
            REQUEST_PUSH -> PushOperation(this)
            REQUEST_SYNC -> SyncOperation(this)
            BREAK_OUT_OF_DETACHED -> BreakOutOfDetached(this)
            REQUEST_RESET -> ResetToRemoteOperation(this)
            else -> {
                tag(TAG).e { "Operation not recognized : $operation" }
                return Result.failure(IllegalArgumentException("$operation is not a valid Git operation"))
            }
        }
        return op.executeAfterAuthentication(GitSettings.authMode)
    }

    fun defaultSuccessHandler(@Suppress("UNUSED_PARAMETER") nothing: Unit) {
        finish()
    }

    fun defaultErrorHandler(err: Throwable) {
        if (!err.isExplicitlyUserInitiatedError()) {
            getEncryptedPrefs("git_operation").edit {
                remove(PreferenceKeys.HTTPS_PASSWORD)
            }
            sharedPrefs.edit { remove(PreferenceKeys.SSH_OPENKEYSTORE_KEYID) }
            d(err)
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.jgit_error_dialog_title))
                .setMessage(ErrorMessages[err])
                .setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
                    finish()
                }.show()
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

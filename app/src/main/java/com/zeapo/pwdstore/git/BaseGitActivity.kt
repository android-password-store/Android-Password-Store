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
import com.github.michaelbull.result.Err
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.michaelbull.result.Result
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
import com.zeapo.pwdstore.utils.sharedPrefs
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.userauth.UserAuthException

/**
 * Abstract [AppCompatActivity] that holds some information that is commonly shared across git-related
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
    suspend fun launchGitOperation(operation: Int): Result<Unit, Throwable> {
        if (GitSettings.url == null) {
            return Err(IllegalStateException("Git url is not set!"))
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
                return Err(IllegalArgumentException("$operation is not a valid Git operation"))
            }
        }
        return op.executeAfterAuthentication(GitSettings.authMode)
    }

    fun finishOnSuccessHandler(@Suppress("UNUSED_PARAMETER") nothing: Unit) {
        finish()
    }

    fun finishAfterPromptOnErrorHandler(err: Throwable) {
        val error = rootCauseException(err)
        if (!isExplicitlyUserInitiatedError(error)) {
            getEncryptedPrefs("git_operation").edit {
                remove(PreferenceKeys.HTTPS_PASSWORD)
            }
            sharedPrefs.edit { remove(PreferenceKeys.SSH_OPENKEYSTORE_KEYID) }
            d(error)
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.jgit_error_dialog_title))
                .setMessage(ErrorMessages[error])
                .setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
                    finish()
                }.show()
        }
    }

    /**
     * Check if a given [Throwable] is the result of an error caused by the user cancelling the
     * operation.
     */
    private fun isExplicitlyUserInitiatedError(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is SSHException &&
                cause.disconnectReason == DisconnectReason.AUTH_CANCELLED_BY_USER)
                return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Get the real root cause of a [Throwable] by traversing until known wrapping exceptions are no
     * longer found.
     */
    private fun rootCauseException(throwable: Throwable): Throwable {
        var rootCause = throwable
        // JGit's TransportException hides the more helpful SSHJ exceptions.
        // Also, SSHJ's UserAuthException about exhausting available authentication methods hides
        // more useful exceptions.
        while ((rootCause is org.eclipse.jgit.errors.TransportException ||
                rootCause is org.eclipse.jgit.api.errors.TransportException ||
                (rootCause is UserAuthException &&
                    rootCause.message == "Exhausted available authentication methods"))) {
            rootCause = rootCause.cause ?: break
        }
        return rootCause
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

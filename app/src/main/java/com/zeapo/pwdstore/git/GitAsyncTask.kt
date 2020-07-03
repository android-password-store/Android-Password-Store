/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.config.SshjSessionFactory
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.userauth.UserAuthException
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.StatusCommand
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.SshSessionFactory
import java.io.IOException
import java.lang.ref.WeakReference


class GitAsyncTask(
    activity: AppCompatActivity,
    private val operation: GitOperation,
    private val finishWithResultOnEnd: Intent?,
    private val silentlyExecute: Boolean = false
) : AsyncTask<GitCommand<*>, Int, GitAsyncTask.Result>() {

    private val activityWeakReference: WeakReference<AppCompatActivity> = WeakReference(activity)
    private val activity: AppCompatActivity?
        get() = activityWeakReference.get()
    private val context: Context = activity.applicationContext
    private val dialog = ProgressDialog(activity)

    sealed class Result {
        object Ok : Result()
        data class Err(val err: Exception) : Result()
    }

    override fun onPreExecute() {
        if (silentlyExecute) return
        dialog.run {
            setMessage(activity!!.resources.getString(R.string.running_dialog_text))
            setCancelable(false)
            show()
        }
    }

    override fun doInBackground(vararg commands: GitCommand<*>): Result? {
        var nbChanges: Int? = null
        for (command in commands) {
            try {
                when (command) {
                    is StatusCommand -> {
                        // in case we have changes, we want to keep track of it
                        val status = command.call()
                        nbChanges = status.changed.size + status.missing.size
                    }
                    is CommitCommand -> {
                        // the previous status will eventually be used to avoid a commit
                        if (nbChanges == null || nbChanges > 0) command.call()
                    }
                    is PullCommand -> {
                        val result = command.call()
                        val rr = result.rebaseResult
                        if (rr.status === RebaseResult.Status.STOPPED) {
                            return Result.Err(IOException(context.getString(R.string
                                .git_pull_fail_error)))
                        }
                    }
                    is PushCommand -> {
                        for (result in command.call()) {
                            // Code imported (modified) from Gerrit PushOp, license Apache v2
                            for (rru in result.remoteUpdates) {
                                val error = when (rru.status) {
                                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                                        context.getString(R.string.git_push_nff_error)
                                    RemoteRefUpdate.Status.REJECTED_NODELETE,
                                    RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
                                    RemoteRefUpdate.Status.NON_EXISTING,
                                    RemoteRefUpdate.Status.NOT_ATTEMPTED
                                    ->
                                        (activity!!.getString(R.string.git_push_generic_error) + rru.status.name)
                                    RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                                        if
                                            ("non-fast-forward" == rru.message) {
                                            context.getString(R.string.git_push_other_error)
                                        } else {
                                            (context.getString(R.string.git_push_generic_error)
                                                + rru.message)
                                        }
                                    }
                                    else -> null

                                }
                                if (error != null)
                                    Result.Err(IOException(error))
                            }
                        }
                    }
                    else -> {
                        command.call()
                    }
                }
            } catch (e: Exception) {
                return Result.Err(e)
            }
        }
        return Result.Ok
    }

    private fun rootCauseException(e: Exception): Exception {
        var rootCause = e
        // JGit's TransportException hides the more helpful SSHJ exceptions.
        // Also, SSHJ's UserAuthException about exhausting available authentication methods hides
        // more useful exceptions.
        while ((rootCause is org.eclipse.jgit.errors.TransportException ||
                rootCause is org.eclipse.jgit.api.errors.TransportException ||
                (rootCause is UserAuthException &&
                    rootCause.message == "Exhausted available authentication methods"))) {
            rootCause = rootCause.cause as? Exception ?: break
        }
        return rootCause
    }

    private fun isExplicitlyUserInitiatedError(e: Exception): Boolean {
        var cause: Exception? = e
        while (cause != null) {
            if (cause is SSHException &&
                cause.disconnectReason == DisconnectReason.AUTH_CANCELLED_BY_USER)
                return true
            cause = cause.cause as? Exception
        }
        return false
    }

    override fun onPostExecute(maybeResult: Result?) {
        if (!silentlyExecute) dialog.dismiss()
        when (val result = maybeResult ?: Result.Err(IOException("Unexpected error"))) {
            is Result.Err -> {
                if (isExplicitlyUserInitiatedError(result.err)) {
                    // Currently, this is only executed when the user cancels a password prompt
                    // during authentication.
                    if (finishWithResultOnEnd != null) {
                        activity?.setResult(AppCompatActivity.RESULT_CANCELED)
                        activity?.finish()
                    }
                } else {
                    e(result.err)
                    operation.onError(rootCauseException(result.err))
                    if (finishWithResultOnEnd != null) {
                        activity?.setResult(AppCompatActivity.RESULT_CANCELED)
                    }
                }
            }
            is Result.Ok -> {
                operation.onSuccess()
                if (finishWithResultOnEnd != null) {
                    activity?.setResult(AppCompatActivity.RESULT_OK, finishWithResultOnEnd)
                    activity?.finish()
                }
            }
        }
        (SshSessionFactory.getInstance() as? SshjSessionFactory)?.clearCredentials()
        SshSessionFactory.setInstance(null)
    }

}

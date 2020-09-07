/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.git

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.GitException.PullException
import com.zeapo.pwdstore.git.GitException.PushException
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.utils.snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.StatusCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RemoteRefUpdate

class GitCommandExecutor(
    private val activity: FragmentActivity,
    private val operation: GitOperation,
) {

    suspend fun execute(): Result<Unit, Throwable> {
        val snackbar = activity.snackbar(
            message = activity.resources.getString(R.string.git_operation_running),
            length = Snackbar.LENGTH_INDEFINITE,
        )
        // Count the number of uncommitted files
        var nbChanges = 0
        return runCatching {
            for (command in operation.commands) {
                when (command) {
                    is StatusCommand -> {
                        val res = withContext(Dispatchers.IO) {
                            command.call()
                        }
                        nbChanges = res.uncommittedChanges.size
                    }
                    is CommitCommand -> {
                        // the previous status will eventually be used to avoid a commit
                        if (nbChanges > 0) {
                            withContext(Dispatchers.IO) {
                                command
                                    .setAuthor(PersonIdent(GitSettings.authorName, GitSettings.authorEmail))
                                    .call()
                            }
                        }
                    }
                    is PullCommand -> {
                        val result = withContext(Dispatchers.IO) {
                            command.call()
                        }
                        val rr = result.rebaseResult
                        if (rr.status === RebaseResult.Status.STOPPED) {
                            throw PullException.PullRebaseFailed
                        }
                    }
                    is PushCommand -> {
                        val results = withContext(Dispatchers.IO) {
                            command.call()
                        }
                        for (result in results) {
                            // Code imported (modified) from Gerrit PushOp, license Apache v2
                            for (rru in result.remoteUpdates) {
                                when (rru.status) {
                                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> throw PushException.NonFastForward
                                    RemoteRefUpdate.Status.REJECTED_NODELETE,
                                    RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
                                    RemoteRefUpdate.Status.NON_EXISTING,
                                    RemoteRefUpdate.Status.NOT_ATTEMPTED,
                                    -> throw PushException.Generic(rru.status.name)
                                    RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                                        throw if ("non-fast-forward" == rru.message) {
                                            PushException.RemoteRejected
                                        } else {
                                            PushException.Generic(rru.message)
                                        }
                                    }
                                    RemoteRefUpdate.Status.UP_TO_DATE -> {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                activity.applicationContext,
                                                activity.applicationContext.getString(R.string.git_push_up_to_date),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    else -> {
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.IO) {
                            command.call()
                        }
                    }
                }
            }
        }.also {
            snackbar.dismiss()
        }
    }
}

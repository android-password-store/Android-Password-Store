/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.git

import dev.msfjarvis.aps.util.git.GitException.PullException
import dev.msfjarvis.aps.util.git.GitException.PushException
import dev.msfjarvis.aps.util.git.operation.GitOperation
import dev.msfjarvis.aps.util.settings.GitSettings
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
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
    private val operation: GitOperation,
) {

    suspend fun execute(): Result<GitResult, Throwable> {
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
                                val name = GitSettings.authorName.ifEmpty { "root" }
                                val email = GitSettings.authorEmail.ifEmpty { "localhost" }
                                val identity = PersonIdent(name, email)
                                command.setAuthor(identity).setCommitter(identity).call()
                            }
                        }
                    }
                    is PullCommand -> {
                        val result = withContext(Dispatchers.IO) {
                            command.call()
                        }
                        val rr = result.rebaseResult
                        if (rr.status == RebaseResult.Status.STOPPED) {
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
                                        return@runCatching GitResult.AlreadyUpToDate
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
            GitResult.OK
        }
    }
}

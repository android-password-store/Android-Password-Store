/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.git

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import app.passwordstore.R
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.snackbar
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.git.GitException.PullException
import app.passwordstore.util.git.GitException.PushException
import app.passwordstore.util.git.operation.GitOperation
import app.passwordstore.util.settings.GitSettings
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.StatusCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RemoteRefUpdate

class GitCommandExecutor(
  private val activity: FragmentActivity,
  private val operation: GitOperation,
) {

  private val hiltEntryPoint by unsafeLazy {
    EntryPointAccessors.fromApplication(
      activity.applicationContext,
      GitCommandExecutorEntryPoint::class.java,
    )
  }

  suspend fun execute(): Result<Unit, Throwable> {
    val gitSettings = hiltEntryPoint.gitSettings()
    val dispatcherProvider = hiltEntryPoint.dispatcherProvider()
    val snackbar =
      activity.snackbar(
        message = activity.resources.getString(R.string.git_operation_running),
        length = Snackbar.LENGTH_INDEFINITE,
      )
    // Count the number of uncommitted files
    var nbChanges = 0
    return runCatching {
        for (command in operation.commands) {
          when (command) {
            is StatusCommand -> {
              val res = withContext(dispatcherProvider.io()) { command.call() }
              nbChanges = res.uncommittedChanges.size
            }
            is CommitCommand -> {
              // the previous status will eventually be used to avoid a commit
              if (nbChanges > 0) {
                withContext(dispatcherProvider.io()) {
                  val name = gitSettings.authorName.ifEmpty { "root" }
                  val email = gitSettings.authorEmail.ifEmpty { "localhost" }
                  val identity = PersonIdent(name, email)
                  command.setAuthor(identity).setCommitter(identity).call()
                }
              }
            }
            is PullCommand -> {
              val result = withContext(dispatcherProvider.io()) { command.call() }
              if (result.rebaseResult != null) {
                if (!result.rebaseResult.status.isSuccessful) {
                  throw PullException.PullRebaseFailed
                }
              } else if (result.mergeResult != null) {
                if (!result.mergeResult.mergeStatus.isSuccessful) {
                  throw PullException.PullMergeFailed
                }
              }
            }
            is PushCommand -> {
              val results = withContext(dispatcherProvider.io()) { command.call() }
              for (result in results) {
                // Code imported (modified) from Gerrit PushOp, license Apache v2
                for (rru in result.remoteUpdates) {
                  when (rru.status) {
                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                      throw PushException.NonFastForward
                    RemoteRefUpdate.Status.REJECTED_NODELETE,
                    RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
                    RemoteRefUpdate.Status.NON_EXISTING,
                    RemoteRefUpdate.Status.NOT_ATTEMPTED ->
                      throw PushException.Generic(rru.status.name)
                    RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                      throw if ("non-fast-forward" == rru.message) {
                        PushException.RemoteRejected
                      } else {
                        PushException.Generic(rru.message)
                      }
                    }
                    RemoteRefUpdate.Status.UP_TO_DATE -> {
                      withContext(dispatcherProvider.main()) {
                        Toast.makeText(
                            activity,
                            activity.applicationContext.getString(R.string.git_push_up_to_date),
                            Toast.LENGTH_SHORT,
                          )
                          .show()
                      }
                    }
                    else -> {}
                  }
                }
              }
            }
            else -> {
              withContext(dispatcherProvider.io()) { command.call() }
            }
          }
        }
      }
      .also { snackbar.dismiss() }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface GitCommandExecutorEntryPoint {

    fun gitSettings(): GitSettings

    fun dispatcherProvider(): DispatcherProvider
  }
}

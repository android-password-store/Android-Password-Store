/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.git

import android.os.RemoteException
import androidx.annotation.StringRes
import dev.msfjarvis.aps.Application
import dev.msfjarvis.aps.R
import java.net.UnknownHostException

/**
 * Supertype for all Git-related [Exception] s that can be thrown by [GitCommandExecutor.execute].
 */
sealed class GitException(@StringRes res: Int, vararg fmt: String) : Exception(buildMessage(res, *fmt)) {

  override val message = super.message!!

  companion object {

    private fun buildMessage(@StringRes res: Int, vararg fmt: String) =
      Application.instance.resources.getString(res, *fmt)
  }

  /** Encapsulates possible errors from a [org.eclipse.jgit.api.PullCommand]. */
  sealed class PullException(@StringRes res: Int, vararg fmt: String) : GitException(res, *fmt) {

    object PullRebaseFailed : PullException(R.string.git_pull_rebase_fail_error)
    object PullMergeFailed : PullException(R.string.git_pull_merge_fail_error)
  }

  /** Encapsulates possible errors from a [org.eclipse.jgit.api.PushCommand]. */
  sealed class PushException(@StringRes res: Int, vararg fmt: String) : GitException(res, *fmt) {

    object NonFastForward : PushException(R.string.git_push_nff_error)
    object RemoteRejected : PushException(R.string.git_push_other_error)
    class Generic(message: String) : PushException(R.string.git_push_generic_error, message)
  }
}

object ErrorMessages {

  operator fun get(throwable: Throwable?): String {
    val resources = Application.instance.resources
    if (throwable == null) return resources.getString(R.string.git_unknown_error)
    return when (val rootCause = rootCause(throwable)) {
      is GitException -> rootCause.message
      is UnknownHostException -> resources.getString(R.string.git_unknown_host, throwable.message)
      else -> throwable.message ?: resources.getString(R.string.git_unknown_error)
    }
  }

  private fun rootCause(throwable: Throwable): Throwable {
    var cause = throwable
    while (cause.cause != null) {
      if (cause is GitException) break
      val nextCause = cause.cause!!
      if (nextCause is RemoteException) break
      cause = nextCause
    }
    return cause
  }
}

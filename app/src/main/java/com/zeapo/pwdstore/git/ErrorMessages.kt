/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.git

import android.os.RemoteException
import com.zeapo.pwdstore.Application
import com.zeapo.pwdstore.R

/**
 * Supertype for all Git-related [Exception]s that can be thrown by [GitCommandExecutor.execute].
 */
open class GitException(message: String? = null) : Exception(message)

/**
 * Encapsulates possible errors from a [org.eclipse.jgit.api.PullCommand].
 */
class PullException(val reason: Reason) : GitException() {

    enum class Reason {
        REBASE_FAILED
    }
}

class PushException(val reason: Reason, vararg val fmt: String) : GitException() {
    enum class Reason {
        NON_FAST_FORWARD,
        REMOTE_REJECTED,
        GENERIC
    }
}

object ErrorMessages {

    private val PULL_REASON_MAP = mapOf(
        PullException.Reason.REBASE_FAILED to R.string.git_pull_fail_error
    )
    private val PUSH_REASON_MAP = mapOf(
        PushException.Reason.NON_FAST_FORWARD to R.string.git_push_nff_error,
        PushException.Reason.REMOTE_REJECTED to R.string.git_push_other_error,
        PushException.Reason.GENERIC to R.string.git_push_generic_error
    )

    operator fun get(throwable: Throwable?): String {
        val resources = Application.instance.resources
        if (throwable == null) return resources.getString(R.string.git_unknown_error)
        return when (val rootCause = rootCause(throwable)) {
            is PullException -> {
                resources.getString(PULL_REASON_MAP.getValue(rootCause.reason))
            }
            is PushException -> {
                resources.getString(PUSH_REASON_MAP.getValue(rootCause.reason), *rootCause.fmt)
            }
            else -> resources.getString(R.string.git_unknown_error)
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

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.git

/**
 * Result of a Git operation executed by [GitCommandExecutor]
 */
sealed class GitResult {

    /**
     * All good!
     */
    object OK : GitResult()

    /**
     * Push operation succeeded and HEAD is already in sync with remote.
     */
    object AlreadyUpToDate : GitResult()
}

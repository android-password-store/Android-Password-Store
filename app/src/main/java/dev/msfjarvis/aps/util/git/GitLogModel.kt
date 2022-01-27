/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.git

import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.util.extensions.asLog
import dev.msfjarvis.aps.util.extensions.hash
import dev.msfjarvis.aps.util.extensions.time
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import logcat.LogPriority.ERROR
import logcat.logcat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit

private val TAG = GitLogModel::class.java.simpleName

private fun commits(): Iterable<RevCommit> {
  val repo = PasswordRepository.repository
  if (repo == null) {
    logcat(TAG, ERROR) { "Could not access git repository" }
    return listOf()
  }
  return runCatching { Git(repo).log().call() }.getOrElse { e ->
    logcat(TAG, ERROR) { e.asLog("Failed to obtain git commits") }
    listOf()
  }
}

/**
 * Provides [GitCommit] s from a git-log of the password git repository.
 *
 * All commits are acquired on the first request to this object.
 */
class GitLogModel {

  // All commits are acquired here at once. Acquiring the commits in batches would not have been
  // entirely sensible because the amount of computation required to obtain commit number n from
  // the log includes the amount of computation required to obtain commit number n-1 from the log.
  // This is because the commit graph is walked from HEAD to the last commit to obtain.
  // Additionally, tests with 1000 commits in the log have not produced a significant delay in the
  // user experience.
  private val cache: MutableList<GitCommit> by unsafeLazy {
    commits()
      .map { GitCommit(it.hash, it.shortMessage, it.authorIdent.name, it.time) }
      .toMutableList()
  }
  val size = cache.size

  fun get(index: Int): GitCommit? {
    if (index >= size)
      logcat(ERROR) { "Cannot get git commit with index $index. There are only $size." }
    return cache.getOrNull(index)
  }
}

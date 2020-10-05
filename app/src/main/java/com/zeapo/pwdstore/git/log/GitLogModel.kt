/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.git.log

import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.hash
import com.zeapo.pwdstore.utils.time
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit

private fun commits(): Iterable<RevCommit> {
    val repo = PasswordRepository.getRepository(null)
    if (repo == null) {
        e { "Could not access git repository" }
        return listOf()
    }
    return runCatching {
        Git(repo).log().call()
    }.getOrElse { e ->
        e(e) { "Failed to obtain git commits" }
        listOf()
    }
}

/**
 * Provides [GitCommit]s from a git-log of the password git repository.
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
    private val cache: MutableList<GitCommit> by lazy(LazyThreadSafetyMode.NONE) {
        commits().map {
            GitCommit(it.hash, it.shortMessage, it.authorIdent.name, it.time)
        }.toMutableList()
    }
    val size = cache.size

    fun get(index: Int): GitCommit? {
        if (index >= size) e { "Cannot get git commit with index $index. There are only $size." }
        return cache.getOrNull(index)
    }
}

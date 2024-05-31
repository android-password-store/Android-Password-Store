/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.extensions

import app.passwordstore.data.repo.PasswordRepository
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.absolutePathString
import logcat.asLog
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

/** Checks if this [Int] contains the given [flag] */
infix fun Int.hasFlag(flag: Int): Boolean {
  return this and flag == flag
}

/**
 * Checks if this [Path] is in the password repository directory as given by
 * [PasswordRepository.getRepositoryDirectory]
 */
fun Path.isInsideRepository(): Boolean {
  return absolutePathString()
    .contains(PasswordRepository.getRepositoryDirectory().absolutePathString())
}

/**
 * Unique SHA-1 hash of this commit as hexadecimal string.
 *
 * @see RevCommit.getId
 */
val RevCommit.hash: String
  get() = ObjectId.toString(id)

/**
 * Time this commit was made with second precision.
 *
 * @see RevCommit.commitTime
 */
val RevCommit.time: Instant
  get() {
    val epochSeconds = commitTime.toLong()
    return Instant.ofEpochSecond(epochSeconds)
  }

/** Alias to [lazy] with thread safety mode always set to [LazyThreadSafetyMode.NONE]. */
fun <T> unsafeLazy(initializer: () -> T) = lazy(LazyThreadSafetyMode.NONE) { initializer.invoke() }

/** A convenience extension to turn a [Throwable] with a message into a loggable string. */
fun Throwable.asLog(message: String): String = "$message\n${asLog()}"

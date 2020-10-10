/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import java.io.File
import java.util.Date
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

/**
 * The default OpenPGP provider for the app
 */
const val OPENPGP_PROVIDER = "org.sufficientlysecure.keychain"

/**
 * Clears the given [flag] from the value of this [Int]
 */
fun Int.clearFlag(flag: Int): Int {
    return this and flag.inv()
}

/**
 * Checks if this [Int] contains the given [flag]
 */
infix fun Int.hasFlag(flag: Int): Boolean {
    return this and flag == flag
}

/**
 * Checks whether this [File] is a directory that contains [other].
 */
fun File.contains(other: File): Boolean {
    if (!isDirectory)
        return false
    if (!other.exists())
        return false
    val relativePath = runCatching {
        other.relativeTo(this)
    }.getOrElse {
        return false
    }
    // Direct containment is equivalent to the relative path being equal to the filename.
    return relativePath.path == other.name
}

/**
 * Checks if this [File] is in the password repository directory as given
 * by [PasswordRepository.getDirectory]
 */
fun File.isInsideRepository(): Boolean {
    return canonicalPath.contains(PasswordRepository.getDirectory().canonicalPath)
}

/**
 * Recursively lists the files in this [File], skipping any directories it encounters.
 */
fun File.listFilesRecursively() = walkTopDown().filter { !it.isDirectory }.toList()

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
val RevCommit.time: Date
    get() {
        val epochSeconds = commitTime.toLong()
        val epochMilliseconds = epochSeconds * 1000
        return Date(epochMilliseconds)
    }

/**
 * Splits this [String] into an [Array] of [String]s, split on the UNIX LF line ending
 * and stripped of any empty lines.
 */
fun String.splitLines(): Array<String> {
    return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

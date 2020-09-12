/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.repo

import java.io.File

/**
 * Simple interface for operations performed on a password store.
 */
interface Store {

    /**
     * List the passwords in the root directory of the store
     */
    fun listRootPasswords(): Array<File>

    /**
     * List the passwords in [subDir]
     */
    fun listPasswordsBySubDir(subDir: File): Array<File>

    /**
     * Delete [password] from the store
     */
    fun deletePassword(password: File): Boolean

    /**
     * Delete [directory] from the store
     */
    fun deleteDirectory(directory: File): Boolean

    /**
     * Read the text contents of [password] as a [ByteArray]
     */
    fun readFromFile(password: File): ByteArray

    /**
     * Write the given [data] to [password]
     */
    fun writeToFile(password: File, data: ByteArray)
}

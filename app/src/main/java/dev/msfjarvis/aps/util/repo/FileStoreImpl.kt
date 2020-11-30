/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.repo

import dev.msfjarvis.aps.util.extensions.listFilesRecursively
import java.io.File

/**
 * [File] backed implementation of [Store] for use with stores in the hidden data directory.
 */
class FileStoreImpl(val baseDir: File) : Store {

    override fun listRootPasswords(): List<File> {
        return (baseDir.listFiles() ?: emptyArray()).filterNotNull().toList()
    }

    override fun listFiles(subDir: File): List<File> {
        return (subDir.listFiles() ?: emptyArray()).filterNotNull().toList()
    }

    override fun deletePassword(password: File): Boolean {
        return password.delete()
    }

    override fun deleteDirectory(directory: File): Boolean {
        return directory.deleteRecursively()
    }

    override fun readFromFile(password: File): ByteArray {
        return password.readBytes()
    }

    override fun writeToFile(password: File, data: ByteArray) {
        password.writeBytes(data)
    }

    override fun listFilesRecursively(subDir: File): FileTreeWalk {
        return subDir.walkTopDown()
    }
}

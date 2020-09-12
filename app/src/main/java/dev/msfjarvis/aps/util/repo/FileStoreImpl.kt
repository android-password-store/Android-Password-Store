/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.repo

import java.io.File

/**
 * [File] backed implementation of [Store] for use with stores in the hidden data directory.
 */
class FileStoreImpl(val baseDir: File) : Store {

    override fun listRootPasswords(): Array<File> {
        return (baseDir.listFiles() ?: emptyArray()).filterNotNull().toTypedArray()
    }

    override fun listPasswordsBySubDir(subDir: File): Array<File> {
        return (subDir.listFiles() ?: emptyArray()).filterNotNull().toTypedArray()
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
}

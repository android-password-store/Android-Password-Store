/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.repo

import xyz.quaver.io.FileX
import xyz.quaver.io.TreeFileX
import xyz.quaver.io.util.deleteRecursively
import xyz.quaver.io.util.readBytes
import xyz.quaver.io.util.writeBytes
import androidx.core.net.toUri
import dev.msfjarvis.aps.Application
import java.io.File

/**
 * [FileX] backed implementation of [Store] for use with repositories in the external storage
 * directory that cannot be accessed with [File].
 */
class FileXStoreImpl(val baseDir: File) : Store {

    @Suppress("UNCHECKED_CAST")
    override fun listRootPasswords(): Array<File> {
        val rootDir = TreeFileX(Application.instance, baseDir.toUri(), cached = true)
        return rootDir.listFiles() as Array<File>
    }

    @Suppress("UNCHECKED_CAST")
    override fun listPasswordsBySubDir(subDir: File): Array<File> {
        val rootDir = TreeFileX(Application.instance, subDir.toUri(), cached = true)
        return rootDir.listFiles() as Array<File>
    }

    override fun deletePassword(password: File): Boolean {
        return FileX(Application.instance, password).delete()
    }

    override fun deleteDirectory(directory: File): Boolean {
        return TreeFileX(Application.instance, directory.toUri(), cached = true).deleteRecursively()
    }

    override fun readFromFile(password: File): ByteArray {
        val fileX = FileX(Application.instance, password.toUri())
        return fileX.readBytes()!!
    }

    override fun writeToFile(password: File, data: ByteArray) {
        val fileX = FileX(Application.instance, password.toUri())
        fileX.writeBytes(data)
    }
}

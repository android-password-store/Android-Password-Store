/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.repo

import android.net.Uri
import xyz.quaver.io.FileX
import xyz.quaver.io.TreeFileX
import xyz.quaver.io.util.deleteRecursively
import xyz.quaver.io.util.readBytes
import xyz.quaver.io.util.writeBytes
import dev.msfjarvis.aps.Application
import androidx.core.net.toUri
import java.io.File

/**
 * [FileX] backed implementation of [Store] for use with repositories in the external storage
 * directory that cannot be accessed with [File].
 */
class FileXStoreImpl(val baseDir: Uri) : Store {

    override fun listRootPasswords(): List<File> {
        val rootDir = TreeFileX(Application.instance, baseDir, cached = true)
        return rootDir.listFiles().toList<File>()
    }

    override fun listFiles(subDir: File): List<File> {
        val rootDir = TreeFileX(Application.instance, baseDir, cached = true)
        return rootDir.listFiles().filter { it.canonicalPath.contains(subDir.canonicalPath) }.toList<File>()
    }

    override fun deletePassword(password: File): Boolean {
        return FileX(Application.instance, password).delete()
    }

    override fun deleteDirectory(directory: File): Boolean {
        return TreeFileX(Application.instance, directory.toUri(), cached = true).deleteRecursively()
    }

    override fun readFromFile(password: File): ByteArray {
        val fileX = FileX(Application.instance, password)
        return fileX.readBytes()!!
    }

    override fun writeToFile(password: File, data: ByteArray) {
        val fileX = FileX(Application.instance, password)
        fileX.writeBytes(data)
    }

    override fun listFilesRecursively(subDir: File): FileTreeWalk {
        return TreeFileX(Application.instance, ("${baseDir}/${subDir}").toUri(), cached = true).walkTopDown()
    }
}

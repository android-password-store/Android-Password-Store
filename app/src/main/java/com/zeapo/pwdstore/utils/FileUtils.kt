/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import java.io.File

object FileUtils {
    @JvmStatic
    fun listFiles(dir: File, recursive: Boolean): Collection<File> {
        val res = ArrayList<File>()
        val files = dir.listFiles()

        if (files != null && files.isNotEmpty()) {

            files.forEach { file ->
                // Check if the file is a directory and recursive add
                if (file.isDirectory && recursive) {
                    res.addAll(listFiles(file, recursive))
                } else if (!file.isDirectory) {
                    res.add(file)
                }
            }
        }
        return res
    }

    @JvmStatic
    fun getBaseName(filename: String): String {
        // Take the file name along with its extension
        val indexName = filename.lastIndexOf('/')
        val nameWithExtension = filename.substring(indexName + 1)

        // Find the final '.' character in the previously calculated nameWithExtension
        val indexExt = nameWithExtension.lastIndexOf('.')

        // If no '.' is found in the name, we assume this is a directory and return the previously
        // derived nameWithExtensions as-is, otherwise we slice out a substring from the first character
        // to the last occurrence of '.' which we found earlier.
        return if (indexExt == -1)
            nameWithExtension
        else
            nameWithExtension.substring(0, indexExt)
    }
}

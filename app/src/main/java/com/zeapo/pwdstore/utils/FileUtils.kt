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
        //take file's name with extension
        val indexName = filename.lastIndexOf('/')
        val nameWithExtension = filename.substring(indexName + 1)

        //only send file's name
        val indexExt = nameWithExtension.lastIndexOf('.')

        //if file doesn't have extension (i.e it's a folder)
        return if (indexExt == -1)
            nameWithExtension
        else
            nameWithExtension.substring(0, indexExt)
    }
}

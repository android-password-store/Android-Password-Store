package com.zeapo.pwdstore.utils

import java.io.File
import java.io.IOException
import java.io.InputStream

object FileUtils {
    @JvmStatic
    fun listFiles(dir: File, extensions: Array<String>?, recursive: Boolean): Collection<File> {
        val res = ArrayList<File>()
        val files = dir.listFiles()

        if (files != null && files.isNotEmpty()) {

            files.forEach { file ->
                // Check if the file is a directory and recursive add
                if (file.isDirectory && recursive) {
                    res.addAll(listFiles(file, extensions, recursive))
                } else if (!file.isDirectory) {
                    if (extensions == null) {
                        //not check extension
                        res.add(file)
                    } else if (extensions.contains(file.extension)) {
                        res.add(file)
                    }
                }
            }
        }

        return res
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyInputStreamToFile(source: InputStream?, destination: File) {
        val output = destination.outputStream()
        source!!.copyTo(output, 1024)
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

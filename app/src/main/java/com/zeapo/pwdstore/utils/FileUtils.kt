package com.zeapo.pwdstore.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset

class FileUtils {
    companion object {

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

            return res;
        }

        @JvmStatic
        fun readFileToString(file: File, charset: Charset): String {
            return file.readText(charset)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun openInputStream(file: File): FileInputStream {
            if (file.exists()) {
                if (file.isDirectory) {
                    throw IOException("File '$file' exists but is a directory")
                }
                if (!file.canRead()) {
                    throw IOException("File '$file' cannot be read")
                }
            } else {
                throw FileNotFoundException("File '$file' does not exist")
            }
            return FileInputStream(file)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun openOutputStream(file: File): FileOutputStream? {
            if (file.exists()) {
                if (file.isDirectory) {
                    throw IOException("File '$file' exists but is a directory")
                }
                if (!file.canWrite()) {
                    throw IOException("File '$file' cannot be written to")
                }
            } else {
                val parent = file.parentFile
                if (parent != null) {
                    if (!parent.mkdirs() && !parent.isDirectory) {
                        throw IOException("Directory '$parent' could not be created")
                    }
                }
            }
            return FileOutputStream(file, false)
        }
    }
}
package com.zeapo.pwdstore.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

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

    @JvmStatic
    @Throws(IOException::class)
    fun copyInputStreamToFile(source: InputStream?, destination: File) {
        val output = openOutputStream(destination)
        source!!.copyTo(output!!, 1024)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun cleanDirectory(directory: File) {
        val files = directory.listFiles()

        files!!.forEach { file ->
            //remove files inside directory
            if (file.isDirectory)
                cleanDirectory(file)

            //remove file or directory
            if (!file.delete())
                throw IOException("Can't delete file $file")
        }
    }

    @JvmStatic
    fun deleteQuietly(file: File?): Boolean {
        return if (file != null) {
            //remove files inside directory
            if (file.isDirectory)
                cleanDirectory(file)

            //remove file or directory
            file.delete()
        } else false
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

package com.zeapo.pwdstore.utils

import java.io.File
import java.nio.charset.Charset

class FileUtils {
    companion object{

        @JvmStatic
        fun listFiles(dir: File, extensions: Array<String>?, recursive: Boolean) : Collection<File>{
            val res = ArrayList<File>()
            val files = dir.listFiles()

            if (files != null && files.isNotEmpty()) {
                files.forEach { file ->
                    // Check if the file is a directory and recursive add
                    if (file.isDirectory && recursive) {
                        res.addAll(listFiles(file, extensions, recursive))
                    } else if (!file.isDirectory){
                        if (extensions == null){
                            //not check extension
                            res.add(file)
                        }else if (extensions.contains(file.extension)){
                            res.add(file)
                        }
                    }
                }
            }

            return res;
        }

        @JvmStatic
        fun readFileToString(file : File, charset: Charset) : String {
            return file.readText(charset)
        }
    }
}
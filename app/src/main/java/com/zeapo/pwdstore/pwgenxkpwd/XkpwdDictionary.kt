/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.R
import java.io.File
import java.util.ArrayList
import java.util.HashMap

class XkpwdDictionary(context: Context) {
    val words: HashMap<Int, ArrayList<String>> = HashMap()

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        var lines: List<String>? = null

        if (prefs.getBoolean("pref_key_is_custom_dict", false)) {

            val uri = prefs.getString("pref_key_custom_dict", "")

            if (!TextUtils.isEmpty(uri)) {
                val customDictFile = File(context.filesDir.toString(), XKPWD_CUSTOM_DICT_FILE)

                if (customDictFile.exists() && customDictFile.canRead()) {
                    lines = customDictFile.inputStream().bufferedReader().readLines()
                }
            }
        }

        if (lines == null || lines.isEmpty()) {
            lines = context.getResources().openRawResource(R.raw.xkpwdict).bufferedReader().readLines()
        }

        for (word in lines) {
            if (!word.trim { it <= ' ' }.contains(" ")) {
                val length = word.trim { it <= ' ' }.length

                if (length > 0) {
                    if (!words.containsKey(length)) {
                        words[length] = ArrayList()
                    }
                    val strings = words[length]!!
                    strings.add(word.trim { it <= ' ' })
                }
            }
        }
    }

    companion object {
        const val XKPWD_CUSTOM_DICT_FILE = "custom_dict.txt"
    }
}

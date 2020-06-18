/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.R
import java.io.File

class XkpwdDictionary(context: Context) {
    val words: Map<Int, List<String>>

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uri = prefs.getString("pref_key_custom_dict", "")!!
        val customDictFile = File(context.filesDir, XKPWD_CUSTOM_DICT_FILE)

        val lines = if (prefs.getBoolean("pref_key_is_custom_dict", false) &&
            uri.isNotEmpty() && customDictFile.canRead()) {
            customDictFile.readLines()
        } else {
            context.resources.openRawResource(R.raw.xkpwdict).bufferedReader().readLines()
        }

        words = lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') }
            .groupBy { it.length }
    }

    companion object {
        const val XKPWD_CUSTOM_DICT_FILE = "custom_dict.txt"
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PreferenceKeys
import java.io.File

class XkpwdDictionary(context: Context) {
    val words: Map<Int, List<String>>

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uri = prefs.getString(PreferenceKeys.PREF_KEY_CUSTOM_DICT, "")!!
        val customDictFile = File(context.filesDir, XKPWD_CUSTOM_DICT_FILE)

        val lines = if (prefs.getBoolean(PreferenceKeys.PREF_KEY_IS_CUSTOM_DICT, false) &&
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

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.pwgenxkpwd

import android.content.Context
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.File

class XkpwdDictionary(context: Context) {

    val words: Map<Int, List<String>>

    init {
        val prefs = context.sharedPrefs
        val uri = prefs.getString(PreferenceKeys.PREF_KEY_CUSTOM_DICT) ?: ""
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

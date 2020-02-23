/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.content.ClipData
import android.content.ClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object ClipboardUtils {

    suspend fun clearClipboard(clipboard: ClipboardManager, deepClear: Boolean = false) {
        Timber.d("Clearing the clipboard")
        val clip = ClipData.newPlainText("pgp_handler_result_pm", "")
        clipboard.primaryClip = clip
        if (deepClear) {
            withContext(Dispatchers.IO) {
                repeat(20) {
                    val count = (it * 500).toString()
                    clipboard.primaryClip = ClipData.newPlainText(count, count)
                }
            }
        }
    }
}

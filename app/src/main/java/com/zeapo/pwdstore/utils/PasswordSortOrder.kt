/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import android.content.Context
import android.content.SharedPreferences
import com.zeapo.pwdstore.Application

enum class PasswordSortOrder(val comparator: java.util.Comparator<PasswordItem>) {

    FOLDER_FIRST(Comparator { p1: PasswordItem, p2: PasswordItem ->
        (p1.type + p1.name)
            .compareTo(p2.type + p2.name, ignoreCase = true)
    }),

    INDEPENDENT(Comparator { p1: PasswordItem, p2: PasswordItem ->
        p1.name.compareTo(p2.name, ignoreCase = true)
    }),

    RECENTLY_USED(Comparator { p1: PasswordItem, p2: PasswordItem ->
        val recentHistory = Application.instance.getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
        val timeP1 = recentHistory.getString(p1.file.absolutePath.base64())
        val timeP2 = recentHistory.getString(p2.file.absolutePath.base64())
        when {
            timeP1 != null && timeP2 != null -> timeP2.compareTo(timeP1)
            timeP1 != null && timeP2 == null -> return@Comparator -1
            timeP1 == null && timeP2 != null -> return@Comparator 1
            else -> p1.name.compareTo(p2.name, ignoreCase = true)
        }
    }),

    FILE_FIRST(Comparator { p1: PasswordItem, p2: PasswordItem ->
        (p2.type + p1.name).compareTo(p1.type + p2.name, ignoreCase = true)
    });

    companion object {

        @JvmStatic
        fun getSortOrder(settings: SharedPreferences): PasswordSortOrder {
            return valueOf(settings.getString(PreferenceKeys.SORT_ORDER) ?: FOLDER_FIRST.name)
        }
    }
}

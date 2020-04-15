/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.TypedValue
import android.view.autofill.AutofillManager
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

infix fun Int.hasFlag(flag: Int): Boolean {
    return this and flag == flag
}

fun String.splitLines(): Array<String> {
    return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

fun Context.resolveAttribute(attr: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.getEncryptedPrefs(fileName: String): SharedPreferences {
    val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)
    return EncryptedSharedPreferences.create(
            fileName,
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

val Context.autofillManager: AutofillManager?
    @RequiresApi(Build.VERSION_CODES.O)
    get() = getSystemService(AutofillManager::class.java)

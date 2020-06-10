/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File

infix fun Int.hasFlag(flag: Int): Boolean {
    return this and flag == flag
}

fun String.splitLines(): Array<String> {
    return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

fun CharArray.clear() {
    forEachIndexed { i, _ ->
        this[i] = 0.toChar()
    }
}

fun File.listFilesRecursively() = walkTopDown().filter { !it.isDirectory }.toList()

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

/**
 * Extension function for [AlertDialog] that requests focus for the
 * view whose id is [id]. Solution based on a StackOverflow
 * answer: https://stackoverflow.com/a/13056259/297261
 */
fun <T : View> AlertDialog.requestInputFocusOnView(@IdRes id: Int) {
    setOnShowListener {
        findViewById<T>(id)?.apply {
            setOnFocusChangeListener { v, _ ->
                v.post {
                    context.getSystemService<InputMethodManager>()
                        ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            requestFocus()
        }
    }
}

val Context.autofillManager: AutofillManager?
    @RequiresApi(Build.VERSION_CODES.O)
    get() = getSystemService()

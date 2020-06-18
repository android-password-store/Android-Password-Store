/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.ajalt.timberkt.d
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.git.GitAsyncTask
import com.zeapo.pwdstore.git.GitOperation
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import org.eclipse.jgit.api.Git
import java.io.File

fun Int.clearFlag(flag: Int): Int {
    return this and flag.inv()
}

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

val Context.clipboard get() = getSystemService<ClipboardManager>()

fun Activity.snackbar(
    view: View = findViewById(android.R.id.content),
    message: String,
    length: Int = Snackbar.LENGTH_SHORT
) {
    Snackbar.make(view, message, length).show()
}

fun File.listFilesRecursively() = walkTopDown().filter { !it.isDirectory }.toList()

fun Context.resolveAttribute(attr: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.getEncryptedPrefs(fileName: String): SharedPreferences {
    val masterKeyAlias = MasterKey.Builder(applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        applicationContext,
        fileName,
        masterKeyAlias,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

@MainThread
fun Activity.commitChange(message: String, finishWithResultOnEnd: Intent? = null) {
    if (!PasswordRepository.isGitRepo()) {
        if (finishWithResultOnEnd != null) {
            setResult(Activity.RESULT_OK, finishWithResultOnEnd)
            finish()
        }
        return
    }
    object : GitOperation(getRepositoryDirectory(this@commitChange), this@commitChange) {
        override fun execute() {
            d { "Comitting with message: '$message'" }
            val git = Git(repository)
            val task = GitAsyncTask(this@commitChange, true, this, finishWithResultOnEnd, silentlyExecute = true)
            task.execute(
                git.add().addFilepattern("."),
                git.commit().setAll(true).setMessage(message)
            )
        }
    }.execute()
}

/**
 * Extension function for [AlertDialog] that requests focus for the
 * view whose id is [id]. Solution based on a StackOverflow
 * answer: https://stackoverflow.com/a/13056259/297261
 */
@MainThread
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

fun Activity.isInsideRepository(file: File): Boolean {
    return file.canonicalPath.contains(getRepositoryDirectory(this).canonicalPath)
}

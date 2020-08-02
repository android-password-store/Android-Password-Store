/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.ajalt.timberkt.d
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.git.GitCommandExecutor
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import java.io.File

const val OPENPGP_PROVIDER = "org.sufficientlysecure.keychain"

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

fun AppCompatActivity.snackbar(
    view: View = findViewById(android.R.id.content),
    message: String,
    length: Int = Snackbar.LENGTH_SHORT,
): Snackbar {
    val snackbar = Snackbar.make(view, message, length)
    snackbar.show()
    return snackbar
}

fun File.listFilesRecursively() = walkTopDown().filter { !it.isDirectory }.toList()

/**
 * Checks whether this [File] is a directory that contains [other].
 */
fun File.contains(other: File): Boolean {
    if (!isDirectory)
        return false
    if (!other.exists())
        return false
    val relativePath = try {
        other.relativeTo(this)
    } catch (e: Exception) {
        return false
    }
    // Direct containment is equivalent to the relative path being equal to the filename.
    return relativePath.path == other.name
}

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

suspend fun AppCompatActivity.commitChange(message: String, finishWithResultOnEnd: Intent? = null) {
    if (!PasswordRepository.isGitRepo()) {
        if (finishWithResultOnEnd != null) {
            setResult(AppCompatActivity.RESULT_OK, finishWithResultOnEnd)
            finish()
        }
        return
    }
    object : GitOperation(getRepositoryDirectory(this@commitChange), this@commitChange) {
        override val commands = arrayOf(
            git.add().addFilepattern("."),
            git.commit().setAll(true).setMessage(message),
        )

        override suspend fun execute() {
            d { "Comitting with message: '$message'" }
            GitCommandExecutor(this@commitChange, this, finishWithResultOnEnd).execute()
        }
    }.execute()
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

fun AppCompatActivity.isInsideRepository(file: File): Boolean {
    return file.canonicalPath.contains(getRepositoryDirectory(this).canonicalPath)
}

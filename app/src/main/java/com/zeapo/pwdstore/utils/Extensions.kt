/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.ajalt.timberkt.d
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import java.io.File
import java.util.Date
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

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

fun String.base64(): String {
    return Base64.encodeToString(encodeToByteArray(), Base64.NO_WRAP)
}

val Context.clipboard get() = getSystemService<ClipboardManager>()

fun FragmentActivity.snackbar(
    view: View = findViewById(android.R.id.content),
    message: String,
    length: Int = Snackbar.LENGTH_SHORT,
): Snackbar {
    val snackbar = Snackbar.make(view, message, length)
    snackbar.anchorView = findViewById(R.id.fab)
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

val Context.sharedPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

fun SharedPreferences.getString(key: String): String? = getString(key, null)

suspend fun FragmentActivity.commitChange(
    message: String,
): Result<Unit, Throwable> {
    if (!PasswordRepository.isGitRepo()) {
        return Ok(Unit)
    }
    return object : GitOperation(this@commitChange) {
        override val commands = arrayOf(
            // Stage all files
            git.add().addFilepattern("."),
            // Populate the changed files count
            git.status(),
            // Commit everything! If anything changed, that is.
            git.commit().setAll(true).setMessage(message),
        )

        override fun preExecute(): Boolean {
            d { "Committing with message: '$message'" }
            return true
        }
    }.execute()
}

fun FragmentActivity.checkRuntimePermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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

val Context.keyguardManager: KeyguardManager
    get() = getSystemService()!!

fun File.isInsideRepository(): Boolean {
    return canonicalPath.contains(getRepositoryDirectory().canonicalPath)
}

/**
 * Unique SHA-1 hash of this commit as hexadecimal string.
 *
 * @see RevCommit.id
 */
val RevCommit.hash: String
    get() = ObjectId.toString(id)

/**
 * Time this commit was made with second precision.
 *
 * @see RevCommit.commitTime
 */
val RevCommit.time: Date
    get() {
        val epochSeconds = commitTime.toLong()
        val epochMilliseconds = epochSeconds * 1000
        return Date(epochMilliseconds)
    }

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.extensions

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.passwordstore.BuildConfig
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.util.git.operation.GitOperation
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.google.android.material.snackbar.Snackbar
import logcat.logcat

/** Get an instance of [AutofillManager]. Only available on Android Oreo and above */
val Context.autofillManager: AutofillManager?
  get() = getSystemService()

/** Get an instance of [ClipboardManager] */
val Context.clipboard
  get() = getSystemService<ClipboardManager>()

/** Wrapper for [getEncryptedPrefs] to avoid open-coding the file name at each call site */
fun Context.getEncryptedGitPrefs() = getEncryptedPrefs("git_operation")

/** Get an instance of [EncryptedSharedPreferences] with the given [fileName] */
private fun Context.getEncryptedPrefs(fileName: String): SharedPreferences {
  val masterKeyAlias =
    MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
  return EncryptedSharedPreferences.create(
    applicationContext,
    fileName,
    masterKeyAlias,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )
}

/** Get an instance of [KeyguardManager] */
val Context.keyguardManager: KeyguardManager
  get() = getSystemService()!!

/** Get the default [SharedPreferences] instance */
val Context.sharedPrefs: SharedPreferences
  get() = getSharedPreferences("${BuildConfig.APPLICATION_ID}_preferences", 0)

/** Resolve [attr] from the [Context]'s theme */
fun Context.resolveAttribute(attr: Int): Int {
  val typedValue = TypedValue()
  this.theme.resolveAttribute(attr, typedValue, true)
  return typedValue.data
}

/**
 * Commit changes to the store from a [FragmentActivity] using a custom implementation of
 * [GitOperation]
 */
suspend fun FragmentActivity.commitChange(message: String): Result<Unit, Throwable> {
  if (!PasswordRepository.isInitialized) {
    return Ok(Unit)
  }
  return object : GitOperation(this@commitChange) {
      override val commands =
        arrayOf(
          // Stage all files
          git.add().addFilepattern("."),
          // Populate the changed files count
          git.status(),
          // Commit everything! If anything changed, that is.
          git.commit().setAll(true).setMessage(message),
        )

      override fun preExecute(): Boolean {
        logcat { "Committing with message: '$message'" }
        return true
      }
    }
    .execute()
}

/** Check if [permission] has been granted to the app. */
fun FragmentActivity.isPermissionGranted(permission: String): Boolean {
  return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Show a [Snackbar] in a [FragmentActivity] and correctly anchor it to a
 * [com.google.android.material.floatingactionbutton.FloatingActionButton] if one exists in the
 * [view]
 */
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

/** Launch an activity denoted by [clazz]. */
fun <T : ComponentActivity> ComponentActivity.launchActivity(clazz: Class<T>) {
  startActivity(Intent(this, clazz).setAction(Intent.ACTION_VIEW))
}

/** Simplifies the common `getString(key, null) ?: defaultValue` case slightly */
fun SharedPreferences.getString(key: String): String? = getString(key, null)

/** Convert this [String] to its [Base64] representation */
fun String.base64(): String {
  return Base64.encodeToString(encodeToByteArray(), Base64.NO_WRAP)
}

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
  } else {
    getPackageInfo(packageName, flags)
  }
}

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int): ApplicationInfo {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getApplicationInfo(packageName, ApplicationInfoFlags.of(flags.toLong()))
  } else {
    getApplicationInfo(packageName, flags)
  }
}

/** Allows conditionally applying the given [modifier] if [isEnabled] is `true`. */
fun Modifier.conditional(isEnabled: Boolean, modifier: Modifier.() -> Modifier): Modifier {
  return if (isEnabled) {
    then(modifier())
  } else {
    this
  }
}

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import app.passwordstore.ui.crypto.BasePGPActivity
import app.passwordstore.ui.crypto.DecryptActivity
import app.passwordstore.ui.passwords.PasswordStore
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.settings.PreferenceKeys

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val prefs = sharedPrefs
    if (prefs.getBoolean(PreferenceKeys.BIOMETRIC_AUTH_2, false)) {
      BiometricAuthenticator.authenticate(this) { result ->
        when (result) {
          is Result.Success -> {
            startTargetActivity(false)
          }
          is Result.HardwareUnavailableOrDisabled -> {
            prefs.edit { remove(PreferenceKeys.BIOMETRIC_AUTH_2) }
            startTargetActivity(false)
          }
          is Result.Failure,
          Result.CanceledBySystem,
          Result.CanceledByUser -> {
            finish()
          }
          is Result.Retry -> {}
        }
      }
    } else {
      startTargetActivity(true)
    }
  }

  private fun getDecryptIntent(): Intent {
    return Intent(this, DecryptActivity::class.java)
  }

  private fun startTargetActivity(noAuth: Boolean) {
    val intentToStart =
      if (intent.action == ACTION_DECRYPT_PASS)
        getDecryptIntent().apply {
          putExtra(
            BasePGPActivity.EXTRA_FILE_PATH,
            intent.getStringExtra(BasePGPActivity.EXTRA_FILE_PATH),
          )
          putExtra(
            BasePGPActivity.EXTRA_REPO_PATH,
            intent.getStringExtra(BasePGPActivity.EXTRA_REPO_PATH),
          )
        }
      else Intent(this, PasswordStore::class.java).setAction(Intent.ACTION_VIEW)
    startActivity(intentToStart)

    Handler(Looper.getMainLooper()).postDelayed({ finish() }, if (noAuth) 0L else 500L)
  }

  companion object {

    const val ACTION_DECRYPT_PASS = "DECRYPT_PASS"
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.ui.crypto.BasePgpActivity
import dev.msfjarvis.aps.ui.crypto.DecryptActivity
import dev.msfjarvis.aps.ui.crypto.DecryptActivityV2
import dev.msfjarvis.aps.ui.passwords.PasswordStore
import dev.msfjarvis.aps.util.auth.BiometricAuthenticator
import dev.msfjarvis.aps.util.auth.BiometricAuthenticator.Result
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.features.Feature
import dev.msfjarvis.aps.util.features.Features
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import javax.inject.Inject

@AndroidEntryPoint
class LaunchActivity : AppCompatActivity() {

  @Inject lateinit var features: Features

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val prefs = sharedPrefs
    if (prefs.getBoolean(PreferenceKeys.BIOMETRIC_AUTH, false)) {
      BiometricAuthenticator.authenticate(this) { result ->
        when (result) {
          is Result.Success -> {
            startTargetActivity(false)
          }
          is Result.HardwareUnavailableOrDisabled -> {
            prefs.edit { remove(PreferenceKeys.BIOMETRIC_AUTH) }
            startTargetActivity(false)
          }
          is Result.Failure,
          Result.Cancelled -> {
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
    return if (features.isEnabled(Feature.EnablePGPainlessBackend)) {
      Intent(this, DecryptActivityV2::class.java)
    } else {
      Intent(this, DecryptActivity::class.java)
    }
  }

  private fun startTargetActivity(noAuth: Boolean) {
    val intentToStart =
      if (intent.action == ACTION_DECRYPT_PASS)
        getDecryptIntent().apply {
          putExtra(
            BasePgpActivity.EXTRA_FILE_PATH,
            intent.getStringExtra(BasePgpActivity.EXTRA_FILE_PATH)
          )
          putExtra(
            BasePgpActivity.EXTRA_REPO_PATH,
            intent.getStringExtra(BasePgpActivity.EXTRA_REPO_PATH)
          )
        }
      else Intent(this, PasswordStore::class.java)
    startActivity(intentToStart)

    Handler(Looper.getMainLooper()).postDelayed({ finish() }, if (noAuth) 0L else 500L)
  }

  companion object {

    const val ACTION_DECRYPT_PASS = "DECRYPT_PASS"
  }
}

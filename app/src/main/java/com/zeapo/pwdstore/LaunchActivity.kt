/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.zeapo.pwdstore.crypto.DecryptActivity
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.sharedPrefs

class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = sharedPrefs
        if (prefs.getBoolean(PreferenceKeys.BIOMETRIC_AUTH, false)) {
            BiometricAuthenticator.authenticate(this) {
                when (it) {
                    is BiometricAuthenticator.Result.Success -> {
                        startTargetActivity(false)
                    }
                    is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> {
                        prefs.edit { remove(PreferenceKeys.BIOMETRIC_AUTH) }
                        startTargetActivity(false)
                    }
                    is BiometricAuthenticator.Result.Failure, BiometricAuthenticator.Result.Cancelled -> {
                        finish()
                    }
                }
            }
        } else {
            startTargetActivity(true)
        }
    }

    private fun startTargetActivity(noAuth: Boolean) {
        val intentToStart = if (intent.action == ACTION_DECRYPT_PASS)
            Intent(this, DecryptActivity::class.java).apply {
                putExtra("NAME", intent.getStringExtra("NAME"))
                putExtra("FILE_PATH", intent.getStringExtra("FILE_PATH"))
                putExtra("REPO_PATH", intent.getStringExtra("REPO_PATH"))
                putExtra("LAST_CHANGED_TIMESTAMP", intent.getLongExtra("LAST_CHANGED_TIMESTAMP", 0L))
            }
        else
            Intent(this, PasswordStore::class.java)
        startActivity(intentToStart)

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, if (noAuth) 0L else 500L)
    }

    companion object {

        const val ACTION_DECRYPT_PASS = "DECRYPT_PASS"
    }
}

/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.utils.auth.AuthenticationResult
import com.zeapo.pwdstore.utils.auth.Authenticator

class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("biometric_auth", false)) {
            Authenticator(this) {
                when (it) {
                    is AuthenticationResult.Success -> {
                        startTargetActivity()
                    }
                    is AuthenticationResult.UnrecoverableError -> {
                        finish()
                    }
                    else -> {
                    }
                }
            }.authenticate()
        } else {
            startTargetActivity()
        }
    }

    private fun startTargetActivity() {
        if (intent?.getStringExtra("OPERATION") == "DECRYPT") {
            val decryptIntent = Intent(this, PgpActivity::class.java)
            decryptIntent.putExtra("NAME", intent.getStringExtra("NAME"))
            decryptIntent.putExtra("FILE_PATH", intent.getStringExtra("FILE_PATH"))
            decryptIntent.putExtra("REPO_PATH", intent.getStringExtra("REPO_PATH"))
            decryptIntent.putExtra("LAST_CHANGED_TIMESTAMP", intent.getLongExtra("LAST_CHANGED_TIMESTAMP", 0L))
            decryptIntent.putExtra("OPERATION", "DECRYPT")
            startActivity(decryptIntent)
        } else {
            startActivity(Intent(this, PasswordStore::class.java))
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

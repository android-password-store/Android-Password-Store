/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.michaelbull.result.fold
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.crypto.DecryptActivity
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.isInitialized
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.sharedPrefs
import kotlinx.coroutines.launch

class LaunchActivity : BaseGitActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
        supportActionBar?.hide()
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
        lifecycleScope.launch {
        }
        if (intent.action == ACTION_DECRYPT_PASS) {
            val intent = Intent(this, DecryptActivity::class.java).apply {
                putExtra("NAME", intent.getStringExtra("NAME"))
                putExtra("FILE_PATH", intent.getStringExtra("FILE_PATH"))
                putExtra("REPO_PATH", intent.getStringExtra("REPO_PATH"))
                putExtra("LAST_CHANGED_TIMESTAMP", intent.getLongExtra("LAST_CHANGED_TIMESTAMP", 0L))
                startPasswordStoreActivity(intent, noAuth)
            }
        } else {
            val intent = Intent(this, PasswordStore::class.java)
            if (isInitialized && !GitSettings.url.isNullOrEmpty() && sharedPrefs.getBoolean(PreferenceKeys.SYNC_ON_LAUNCH, false)) {
                findViewById<ProgressBar>(R.id.progress_bar).isVisible = true
                findViewById<ProgressBar>(R.id.sync).isVisible = true
                runGitOperation(GitOp.SYNC, intent)
            } else {
                startPasswordStoreActivity(intent, noAuth)
            }
        }
    }

    private fun startPasswordStoreActivity(intent: Intent, noAuth: Boolean) {
        Handler().postDelayed({
            startActivity(intent)
            finish()
        }, 1000L)
    }

    private fun runGitOperation(operation: GitOp, intent: Intent) = lifecycleScope.launch {
        launchGitOperation(operation).fold(
            success = { startPasswordStoreActivity(intent, false) },
            failure = { promptOnErrorHandler(it) { startPasswordStoreActivity(intent, false) } },
        )
    }

    companion object {

        const val ACTION_DECRYPT_PASS = "DECRYPT_PASS"
    }
}

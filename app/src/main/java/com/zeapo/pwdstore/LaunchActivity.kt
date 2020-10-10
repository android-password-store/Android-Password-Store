/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.michaelbull.result.fold
import com.zeapo.pwdstore.crypto.DecryptActivity
import com.zeapo.pwdstore.databinding.ActivityLaunchBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.config.AuthMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.launch

class LaunchActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityLaunchBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
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
        if (intent.action == ACTION_DECRYPT_PASS) {
            val intent = Intent(this, DecryptActivity::class.java).apply {
                putExtra("NAME", intent.getStringExtra("NAME"))
                putExtra("FILE_PATH", intent.getStringExtra("FILE_PATH"))
                putExtra("REPO_PATH", intent.getStringExtra("REPO_PATH"))
                putExtra("LAST_CHANGED_TIMESTAMP", intent.getLongExtra("LAST_CHANGED_TIMESTAMP", 0L))
            }
            startPasswordStoreActivity(intent, noAuth)
        } else {
            val intent = Intent(this, PasswordStore::class.java)
            if (PasswordRepository.getRepository(null) != null &&
                !GitSettings.url.isNullOrEmpty() &&
                sharedPrefs.getBoolean(PreferenceKeys.SYNC_ON_LAUNCH, false)) {
                binding.progressBar.isVisible = true
                binding.sync.isVisible = true
                runGitOperation { startPasswordStoreActivity(intent, noAuth) }
            } else {
                startPasswordStoreActivity(intent, noAuth)
            }
        }
    }

    private fun startPasswordStoreActivity(intent: Intent, noAuth: Boolean) {
        Handler().postDelayed({
            startActivity(intent)
            finish()
        }, if (noAuth) 500L else 0L)
    }

    private fun runGitOperation(onCompletion: () -> Unit) = lifecycleScope.launch {
        val gitOp = if (GitSettings.authMode == AuthMode.None) GitOp.PULL else GitOp.SYNC
        launchGitOperation(gitOp).fold(
            success = { onCompletion.invoke() },
            failure = { promptOnErrorHandler(it) { onCompletion.invoke() } },
        )
    }

    companion object {

        const val ACTION_DECRYPT_PASS = "DECRYPT_PASS"
    }
}

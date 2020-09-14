/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitCloneBinding
import com.zeapo.pwdstore.git.config.AuthMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.snackbar
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity that encompasses both the initial clone as well as editing the server config for future
 * changes.
 */
class GitServerConfigActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitCloneBinding::inflate)

    private lateinit var newAuthMode: AuthMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isClone = intent?.extras?.getBoolean("cloning") ?: false
        if (isClone) {
            binding.saveButton.text = getString(R.string.clone_button)
        }
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        newAuthMode = GitSettings.authMode

        binding.authModeGroup.apply {
            when (newAuthMode) {
                AuthMode.SshKey -> check(R.id.auth_mode_ssh_key)
                AuthMode.Password -> check(R.id.auth_mode_password)
                AuthMode.OpenKeychain -> check(R.id.auth_mode_open_keychain)
                AuthMode.None -> uncheck(checkedButtonId)
            }
            addOnButtonCheckedListener { _, _, _ ->
                when (checkedButtonId) {
                    R.id.auth_mode_ssh_key -> newAuthMode = AuthMode.SshKey
                    R.id.auth_mode_open_keychain -> newAuthMode = AuthMode.OpenKeychain
                    R.id.auth_mode_password -> newAuthMode = AuthMode.Password
                    View.NO_ID -> newAuthMode = AuthMode.None
                }
            }
        }

        binding.serverUrl.setText(GitSettings.url)
        binding.serverBranch.setText(GitSettings.branch)

        binding.serverUrl.doOnTextChanged { text, _, _, _ ->
            if (text.isNullOrEmpty()) return@doOnTextChanged
            if (text.startsWith("http://") || text.startsWith("https://")) {
                binding.authModeSshKey.isVisible = false
                binding.authModeOpenKeychain.isVisible = false
                binding.authModePassword.isVisible = true
            } else {
                binding.authModeSshKey.isVisible = true
                binding.authModeOpenKeychain.isVisible = true
                binding.authModePassword.isVisible = true
            }
        }

        binding.saveButton.setOnClickListener {
            when (val updateResult = GitSettings.updateConnectionSettingsIfValid(
                newAuthMode = newAuthMode,
                newUrl = binding.serverUrl.text.toString().trim(),
                newBranch = binding.serverBranch.text.toString().trim())) {
                GitSettings.UpdateConnectionSettingsResult.FailedToParseUrl -> {
                    Snackbar.make(binding.root, getString(R.string.git_server_config_save_error), Snackbar.LENGTH_LONG).show()
                }
                is GitSettings.UpdateConnectionSettingsResult.MissingUsername -> {
                    when (updateResult.newProtocol) {
                        Protocol.Https -> Snackbar.make(binding.root, getString(R.string.git_server_config_save_missing_username_https), Snackbar.LENGTH_LONG).show()
                        Protocol.Ssh -> Snackbar.make(binding.root, getString(R.string.git_server_config_save_missing_username_ssh), Snackbar.LENGTH_LONG).show()
                    }
                }
                GitSettings.UpdateConnectionSettingsResult.Valid -> {
                    if (isClone && PasswordRepository.getRepository(null) == null)
                        PasswordRepository.initialize()
                    if (!isClone) {
                        Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
                        Handler().postDelayed(500) { finish() }
                    } else {
                        cloneRepository()
                    }
                }
                is GitSettings.UpdateConnectionSettingsResult.AuthModeMismatch -> {
                    val message = getString(
                        R.string.git_server_config_save_auth_mode_mismatch,
                        updateResult.newProtocol,
                        updateResult.validModes.joinToString(", "),
                    )
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Clones the repository, the directory exists, deletes it
     */
    private fun cloneRepository() {
        val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory())
        val localDirFiles = localDir.listFiles() ?: emptyArray()
        // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
        if (localDir.exists() && localDirFiles.isNotEmpty() &&
            !(localDirFiles.size == 1 && localDirFiles[0].name == ".git")) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getString(R.string.dialog_delete_msg, localDir.toString()))
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_delete) { dialog, _ ->
                    runCatching {
                        lifecycleScope.launch {
                            val snackbar = snackbar(message = getString(R.string.delete_directory_progress_text), length = Snackbar.LENGTH_INDEFINITE)
                            withContext(Dispatchers.IO) {
                                localDir.deleteRecursively()
                            }
                            snackbar.dismiss()
                            launchGitOperation(GitOp.CLONE).fold(
                                success = {
                                    setResult(RESULT_OK)
                                    finish()
                                },
                                failure = { err ->
                                    promptOnErrorHandler(err) {
                                        finish()
                                    }
                                }
                            )
                        }
                    }.onFailure { e ->
                        e.printStackTrace()
                        MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                    }
                    dialog.cancel()
                }
                .setNegativeButton(R.string.dialog_do_not_delete) { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        } else {
            runCatching {
                // Silently delete & replace the lone .git folder if it exists
                if (localDir.exists() && localDirFiles.size == 1 && localDirFiles[0].name == ".git") {
                    localDir.deleteRecursively()

                }
            }.onFailure { e ->
                e(e)
                MaterialAlertDialogBuilder(this).setMessage(e.message).show()
            }
            lifecycleScope.launch {
                launchGitOperation(GitOp.CLONE).fold(
                    success = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    failure = { promptOnErrorHandler(it) },
                )
            }
        }
    }

    companion object {
        fun createCloneIntent(context: Context): Intent {
            return Intent(context, GitServerConfigActivity::class.java).apply {
                putExtra("cloning", true)
            }
        }
    }
}

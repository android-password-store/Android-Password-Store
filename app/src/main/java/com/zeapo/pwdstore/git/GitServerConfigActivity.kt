/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.core.os.postDelayed
import androidx.lifecycle.lifecycleScope
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
import java.io.IOException
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
        val isClone = intent?.extras?.getInt(REQUEST_ARG_OP) ?: -1 == REQUEST_CLONE
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
                    try {
                        lifecycleScope.launch {
                            val snackbar = snackbar(message = getString(R.string.delete_directory_progress_text), length = Snackbar.LENGTH_INDEFINITE)
                            withContext(Dispatchers.IO) {
                                localDir.deleteRecursively()
                            }
                            snackbar.dismiss()
                            launchGitOperation(REQUEST_CLONE)
                        }
                    } catch (e: IOException) {
                        // TODO Handle the exception correctly if we are unable to delete the directory...
                        e.printStackTrace()
                        MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                    } finally {
                        dialog.cancel()
                    }
                }
                .setNegativeButton(R.string.dialog_do_not_delete) { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        } else {
            try {
                // Silently delete & replace the lone .git folder if it exists
                if (localDir.exists() && localDirFiles.size == 1 && localDirFiles[0].name == ".git") {
                    try {
                        localDir.deleteRecursively()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                    }
                }
            } catch (e: Exception) {
                // This is what happens when JGit fails :(
                // TODO Handle the different cases of exceptions
                e.printStackTrace()
                MaterialAlertDialogBuilder(this).setMessage(e.message).show()
            }
            lifecycleScope.launch { launchGitOperation(REQUEST_CLONE) }
        }
    }
}

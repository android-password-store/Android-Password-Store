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
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.viewBinding
import java.io.IOException
import kotlinx.coroutines.launch

/**
 * Activity that encompasses both the initial clone as well as editing the server config for future
 * changes.
 */
class GitServerConfigActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitCloneBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isClone = intent?.extras?.getInt(REQUEST_ARG_OP) ?: -1 == REQUEST_CLONE
        if (isClone) {
            binding.saveButton.text = getString(R.string.clone_button)
        }
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.cloneProtocolGroup.check(when (GitSettings.protocol) {
            Protocol.Ssh -> R.id.clone_protocol_ssh
            Protocol.Https -> R.id.clone_protocol_https
        })
        binding.cloneProtocolGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                when (checkedId) {
                    R.id.clone_protocol_https -> GitSettings.protocol = Protocol.Https
                    R.id.clone_protocol_ssh -> GitSettings.protocol = Protocol.Ssh
                }
                updateConnectionModeToggleGroup()
            }
        }

        binding.connectionModeGroup.apply {
            when (GitSettings.connectionMode) {
                ConnectionMode.SshKey -> check(R.id.connection_mode_ssh_key)
                ConnectionMode.Password -> check(R.id.connection_mode_password)
                ConnectionMode.OpenKeychain -> check(R.id.connection_mode_open_keychain)
                ConnectionMode.None -> uncheck(checkedButtonId)
            }
            addOnButtonCheckedListener { _, _, _ ->
                when (checkedButtonId) {
                    R.id.connection_mode_ssh_key -> GitSettings.connectionMode = ConnectionMode.SshKey
                    R.id.connection_mode_open_keychain -> GitSettings.connectionMode = ConnectionMode.OpenKeychain
                    R.id.connection_mode_password -> GitSettings.connectionMode = ConnectionMode.Password
                    View.NO_ID -> GitSettings.connectionMode = ConnectionMode.None
                }
            }
        }
        updateConnectionModeToggleGroup()

        binding.serverUrl.setText(GitSettings.url)
        binding.serverBranch.setText(GitSettings.branch)

        binding.saveButton.setOnClickListener {
            if (isClone && PasswordRepository.getRepository(null) == null)
                PasswordRepository.initialize()
            GitSettings.branch = binding.serverBranch.text.toString().trim()
            if (GitSettings.updateUrlIfValid(binding.serverUrl.text.toString().trim())) {
                if (!isClone) {
                    Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
                    Handler().postDelayed(500) { finish() }
                } else {
                    cloneRepository()
                }
            } else {
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_error), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateConnectionModeToggleGroup() {
        if (GitSettings.protocol == Protocol.Ssh) {
            // Reset connection mode to SSH key if the current value (none) is not valid for SSH
            if (binding.connectionModeGroup.checkedButtonIds.isEmpty())
                binding.connectionModeGroup.check(R.id.connection_mode_ssh_key)
            binding.connectionModeSshKey.isEnabled = true
            binding.connectionModeOpenKeychain.isEnabled = true
            binding.connectionModeGroup.isSelectionRequired = true
        } else {
            binding.connectionModeGroup.isSelectionRequired = false
            // Reset connection mode to password if the current value is not valid for HTTPS
            // Important note: This has to happen before disabling the other toggle buttons or they
            // won't uncheck.
            if (GitSettings.connectionMode !in listOf(ConnectionMode.None, ConnectionMode.Password))
                binding.connectionModeGroup.check(R.id.connection_mode_password)
            binding.connectionModeSshKey.isEnabled = false
            binding.connectionModeOpenKeychain.isEnabled = false
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
                        localDir.deleteRecursively()
                        lifecycleScope.launch { launchGitOperation(REQUEST_CLONE) }
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

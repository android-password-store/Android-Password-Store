/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.os.Bundle
import android.os.Handler
import androidx.core.content.edit
import androidx.core.os.postDelayed
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitCloneBinding
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.IOException

/**
 * Activity that encompasses both the initial clone as well as editing the server config for future
 * changes.
 */
class GitServerConfigActivity : BaseGitActivity() {

    private lateinit var binding: ActivityGitCloneBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGitCloneBinding.inflate(layoutInflater)
        val isClone = intent?.extras?.getInt(REQUEST_ARG_OP) ?: -1 == REQUEST_CLONE
        if (isClone) {
            binding.saveButton.text = getString(R.string.clone_button)
        }
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.cloneProtocolGroup.check(when (protocol) {
            Protocol.Ssh -> R.id.clone_protocol_ssh
            Protocol.Https -> R.id.clone_protocol_https
        })
        binding.cloneProtocolGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                when (checkedId) {
                    R.id.clone_protocol_https -> protocol = Protocol.Https
                    R.id.clone_protocol_ssh -> protocol = Protocol.Ssh
                }
                updateConnectionModeToggleGroup()
            }
        }

        binding.connectionModeGroup.check(when (connectionMode) {
            ConnectionMode.SshKey -> R.id.connection_mode_ssh_key
            ConnectionMode.Password -> R.id.connection_mode_password
            ConnectionMode.OpenKeychain -> R.id.connection_mode_open_keychain
            ConnectionMode.None -> R.id.connection_mode_none
        })
        binding.connectionModeGroup.setOnCheckedChangeListener { group, _ ->
            when (group.checkedRadioButtonId) {
                R.id.connection_mode_ssh_key -> connectionMode = ConnectionMode.SshKey
                R.id.connection_mode_open_keychain -> connectionMode = ConnectionMode.OpenKeychain
                R.id.connection_mode_password -> connectionMode = ConnectionMode.Password
                R.id.connection_mode_none -> connectionMode = ConnectionMode.None
            }
        }
        updateConnectionModeToggleGroup()

        binding.serverUrl.apply {
            setText(serverHostname)
            doOnTextChanged { text, _, _, _ ->
                serverHostname = text.toString().trim()
            }
        }

        binding.serverPort.apply {
            setText(serverPort)
            doOnTextChanged { text, _, _, _ ->
                serverPort = text.toString().trim()
            }
        }

        binding.serverUser.apply {
            setText(serverUser)
            doOnTextChanged { text, _, _, _ ->
                serverUser = text.toString().trim()
            }
        }

        binding.serverPath.apply {
            setText(serverPath)
            doOnTextChanged { text, _, _, _ ->
                serverPath = text.toString().trim()
            }
        }

        binding.saveButton.setOnClickListener {
            if (isClone && PasswordRepository.getRepository(null) == null)
                PasswordRepository.initialize(this)
            if (updateUrl()) {
                settings.edit {
                    putString("git_remote_protocol", protocol.pref)
                    putString("git_remote_auth", connectionMode.pref)
                    putString("git_remote_server", serverHostname)
                    putString("git_remote_port", serverPort)
                    putString("git_remote_username", serverUser)
                    putString("git_remote_location", serverPath)
                }
                if (!isClone) {
                    Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
                    Handler().postDelayed(500) { finish() }
                } else
                    cloneRepository()
            } else {
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_failure), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateConnectionModeToggleGroup() {
        if (protocol == Protocol.Ssh) {
            if (binding.connectionModeNone.isChecked)
                binding.connectionModeGroup.check(R.id.connection_mode_ssh_key)
            binding.connectionModeSshKey.isEnabled = true
            binding.connectionModeOpenKeychain.isEnabled = true
            binding.connectionModeNone.isEnabled = false
        } else {
            // Reset connection mode to password if the current value is not valid for HTTPS
            // Important note: This has to happen before disabling the other toggle buttons or they
            // won't uncheck.
            if (connectionMode !in listOf(ConnectionMode.None, ConnectionMode.Password))
                binding.connectionModeGroup.check(R.id.connection_mode_password)
            binding.connectionModeSshKey.isEnabled = false
            binding.connectionModeOpenKeychain.isEnabled = false
            binding.connectionModeNone.isEnabled = true
        }
    }

    /**
     * Clones the repository, the directory exists, deletes it
     */
    private fun cloneRepository() {
        val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory(this))
        val localDirFiles = localDir.listFiles() ?: emptyArray()
        // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
        if (localDir.exists() && localDirFiles.isNotEmpty() &&
            !(localDirFiles.size == 1 && localDirFiles[0].name == ".git")) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getString(R.string.dialog_delete_msg) + " " + localDir.toString())
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_delete) { dialog, _ ->
                    try {
                        localDir.deleteRecursively()
                        launchGitOperation(REQUEST_CLONE)
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
            launchGitOperation(REQUEST_CLONE)
        }
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.os.Bundle
import androidx.core.content.edit
import androidx.core.widget.doOnTextChanged
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitCloneBinding
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol

/**
 * Activity that encompasses both the initial clone as well as editing the server config for future
 * changes.
 */
class GitServerConfigActivity : BaseGitActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGitCloneBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (protocol) {
            Protocol.Ssh -> binding.cloneProtocolSsh.isChecked = true
            Protocol.Https -> binding.cloneProtocolHttps.isChecked = true
        }

        when (connectionMode) {
            ConnectionMode.Username -> binding.connectionModeUsername.isChecked = true
            ConnectionMode.OpenKeychain -> binding.connectionModeOpenkeychain.isChecked = true
            ConnectionMode.Ssh -> binding.connectionModeSsh.isChecked = true
        }

        binding.cloneProtocolGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                protocol = when (checkedId) {
                    R.id.clone_protocol_https -> Protocol.Https
                    R.id.clone_protocol_ssh -> Protocol.Ssh
                    else -> protocol
                }
            }
        }

        binding.connectionModeGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                connectionMode = when (checkedId) {
                    R.id.connection_mode_ssh -> ConnectionMode.Ssh
                    R.id.connection_mode_openkeychain -> ConnectionMode.OpenKeychain
                    R.id.connection_mode_username -> ConnectionMode.Username
                    else -> connectionMode
                }
            }
        }

        binding.serverUrl.apply {
            setText(serverUrl)
            doOnTextChanged { text, _, _, _ ->
                serverUrl = text.toString()
            }
        }

        binding.serverPort.apply {
            setText(serverPort)
            doOnTextChanged { text, _, _, _ ->
                serverPort = text.toString()
            }
        }

        binding.serverUser.apply {
            setText(serverUser)
            doOnTextChanged { text, _, _, _ ->
                serverUser = text.toString()
            }
        }

        binding.serverPath.apply {
            setText(serverPath)
            doOnTextChanged { text, _, _, _ ->
                serverPath = text.toString()
            }
        }

        binding.saveButton.setOnClickListener {
            if (updateHostname()) {
                settings.edit(true) {
                    putString("git_remote_protocol", protocol.toString())
                    putString("git_remote_auth", connectionMode.toString())
                    putString("git_remote_server", serverUrl)
                    putString("git_remote_port", serverPort)
                    putString("git_remote_username", serverUser)
                    putString("git_remote_location", serverPath)
                }
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_failure), Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

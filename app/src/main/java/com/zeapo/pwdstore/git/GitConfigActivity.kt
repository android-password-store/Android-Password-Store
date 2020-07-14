/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.os.Bundle
import android.os.Handler
import android.util.Patterns
import androidx.core.content.edit
import androidx.core.os.postDelayed
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitConfigBinding
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.viewBinding
import org.eclipse.jgit.lib.Constants

class GitConfigActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitConfigBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (username.isEmpty())
            binding.gitUserName.requestFocus()
        else
            binding.gitUserName.setText(username)
        binding.gitUserEmail.setText(email)
        val repo = PasswordRepository.getRepository(PasswordRepository.getRepositoryDirectory(this))
        if (repo != null) {
            try {
                val objectId = repo.resolve(Constants.HEAD)
                val ref = repo.getRef("refs/heads/master")
                val head = if (ref.objectId.equals(objectId)) ref.name else "DETACHED"
                binding.gitCommitHash.text = String.format("%s (%s)", objectId.abbreviate(8).name(), head)

                // enable the abort button only if we're rebasing
                val isRebasing = repo.repositoryState.isRebasing
                binding.gitAbortRebase.isEnabled = isRebasing
                binding.gitAbortRebase.alpha = if (isRebasing) 1.0f else 0.5f
            } catch (ignored: Exception) {
            }
        }
        binding.gitAbortRebase.setOnClickListener { launchGitOperation(BREAK_OUT_OF_DETACHED) }
        binding.gitResetToRemote.setOnClickListener { launchGitOperation(REQUEST_RESET) }
        binding.saveButton.setOnClickListener {
            val email = binding.gitUserEmail.text.toString().trim()
            val name = binding.gitUserName.text.toString().trim()
            if (!email.matches(Patterns.EMAIL_ADDRESS.toRegex())) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.invalid_email_dialog_text))
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .show()
            } else {
                settings.edit {
                    putString(PreferenceKeys.GIT_CONFIG_USER_EMAIL, email)
                    putString(PreferenceKeys.GIT_CONFIG_USER_NAME, name)
                }
                PasswordRepository.setUserName(name)
                PasswordRepository.setUserEmail(email)
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
                Handler().postDelayed(500) { finish() }
            }
        }
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.os.Bundle
import android.util.Patterns
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitConfigBinding
import com.zeapo.pwdstore.utils.PasswordRepository
import org.eclipse.jgit.lib.Constants

class GitConfigActivity : BaseGitActivity() {

    private lateinit var binding: ActivityGitConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGitConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
                val editor = settings.edit()
                editor.putString("git_config_user_email", email)
                editor.putString("git_config_user_name", name)
                PasswordRepository.setUserName(name)
                PasswordRepository.setUserEmail(email)
                editor.apply()
            }
        }
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Patterns
import androidx.core.os.postDelayed
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitConfigBinding
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.log.GitLogActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.launch
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository

class GitConfigActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitConfigBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (GitSettings.authorName.isEmpty())
            binding.gitUserName.requestFocus()
        else
            binding.gitUserName.setText(GitSettings.authorName)
        binding.gitUserEmail.setText(GitSettings.authorEmail)
        setupTools()
        binding.saveButton.setOnClickListener {
            val email = binding.gitUserEmail.text.toString().trim()
            val name = binding.gitUserName.text.toString().trim()
            if (!email.matches(Patterns.EMAIL_ADDRESS.toRegex())) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.invalid_email_dialog_text))
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .show()
            } else {
                GitSettings.authorEmail = email
                GitSettings.authorName = name
                Snackbar.make(binding.root, getString(R.string.git_server_config_save_success), Snackbar.LENGTH_SHORT).show()
                Handler().postDelayed(500) { finish() }
            }
        }
    }

    /**
     * Sets up the UI components of the tools section.
     */
    private fun setupTools() {
        val repo = PasswordRepository.getRepository(null)
        if (repo != null) {
            binding.gitHeadStatus.text = headStatusMsg(repo)
            // enable the abort button only if we're rebasing
            val isRebasing = repo.repositoryState.isRebasing
            binding.gitAbortRebase.isEnabled = isRebasing
            binding.gitAbortRebase.alpha = if (isRebasing) 1.0f else 0.5f
        }
        binding.gitLog.setOnClickListener {
            try {
                intent = Intent(this, GitLogActivity::class.java)
                startActivity(intent)
            } catch (ex: Exception) {
                e(ex) { "Failed to start GitLogActivity" }
            }
        }
        binding.gitAbortRebase.setOnClickListener { lifecycleScope.launch { launchGitOperation(BREAK_OUT_OF_DETACHED) } }
        binding.gitResetToRemote.setOnClickListener { lifecycleScope.launch { launchGitOperation(REQUEST_RESET) } }
    }

    /**
     * Returns a user-friendly message about the current state of HEAD.
     *
     * The state is recognized to be either pointing to a branch or detached.
     */
    private fun headStatusMsg(repo: Repository): String {
        return try {
            val headRef = repo.getRef(Constants.HEAD)
            if (headRef.isSymbolic) {
                val branchName = headRef.target.name
                val shortBranchName = Repository.shortenRefName(branchName)
                getString(R.string.git_head_on_branch, shortBranchName)
            } else {
                val commitHash = headRef.objectId.abbreviate(8).name()
                getString(R.string.git_head_detached, commitHash)
            }
        } catch (ex: Exception) {
            e(ex) { "Error getting HEAD reference" }
            getString(R.string.git_head_missing)
        }
    }
}

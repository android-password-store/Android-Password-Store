/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.MenuItem
import androidx.core.os.postDelayed
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
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
                Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
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
            runCatching {
                startActivity(Intent(this, GitLogActivity::class.java))
            }.onFailure { ex ->
                e(ex) { "Failed to start GitLogActivity" }
            }
        }
        binding.gitAbortRebase.setOnClickListener {
            lifecycleScope.launch {
                launchGitOperation(GitOp.BREAK_OUT_OF_DETACHED).fold(
                  success = {
                      MaterialAlertDialogBuilder(this@GitConfigActivity).run {
                          setTitle(resources.getString(R.string.git_abort_and_push_title))
                          setMessage(resources.getString(
                            R.string.git_break_out_of_detached_success,
                            GitSettings.branch,
                            "conflicting-${GitSettings.branch}-...",
                          ))
                          setOnDismissListener() { finish() }
                          setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ -> }
                          show()
                      }
                  },
                  failure = { err ->
                      promptOnErrorHandler(err) {
                          finish()
                      }
                  },
                )
            }
        }
        binding.gitResetToRemote.setOnClickListener {
            lifecycleScope.launch {
                launchGitOperation(GitOp.RESET).fold(
                  success = ::finishOnSuccessHandler,
                  failure = { err ->
                      promptOnErrorHandler(err) {
                          finish()
                      }
                  },
                )
            }
        }
    }

    /**
     * Returns a user-friendly message about the current state of HEAD.
     *
     * The state is recognized to be either pointing to a branch or detached.
     */
    private fun headStatusMsg(repo: Repository): String {
        return runCatching {
            val headRef = repo.getRef(Constants.HEAD)
            if (headRef.isSymbolic) {
                val branchName = headRef.target.name
                val shortBranchName = Repository.shortenRefName(branchName)
                getString(R.string.git_head_on_branch, shortBranchName)
            } else {
                val commitHash = headRef.objectId.abbreviate(8).name()
                getString(R.string.git_head_detached, commitHash)
            }
        }.getOrElse { ex ->
            e(ex) { "Error getting HEAD reference" }
            getString(R.string.git_head_missing)
        }
    }
}

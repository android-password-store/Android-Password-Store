/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.git.config

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.MenuItem
import androidx.core.os.postDelayed
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.ActivityGitConfigBinding
import app.passwordstore.ui.dialogs.TextInputDialog
import app.passwordstore.ui.git.base.BaseGitActivity
import app.passwordstore.ui.git.log.GitLogActivity
import app.passwordstore.util.extensions.asLog
import app.passwordstore.util.extensions.launchActivity
import app.passwordstore.util.extensions.viewBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState

class GitConfigActivity : BaseGitActivity() {

  private val binding by viewBinding(ActivityGitConfigBinding::inflate)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    if (gitSettings.authorName.isEmpty()) binding.gitUserName.requestFocus()
    else binding.gitUserName.setText(gitSettings.authorName)
    binding.gitUserEmail.setText(gitSettings.authorEmail)
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
        gitSettings.authorEmail = email
        gitSettings.authorName = name
        Snackbar.make(
            binding.root,
            getString(R.string.git_server_config_save_success),
            Snackbar.LENGTH_SHORT,
          )
          .show()
        Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        onBackPressedDispatcher.onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  /** Sets up the UI components of the tools section. */
  private fun setupTools() {
    val repo = PasswordRepository.repository
    if (repo != null) {
      binding.gitHeadStatus.text = headStatusMsg(repo)
      // enable the abort button only if we're rebasing or merging
      val needsAbort =
        repo.repositoryState.isRebasing || repo.repositoryState == RepositoryState.MERGING
      binding.gitAbortRebase.isEnabled = needsAbort
      binding.gitAbortRebase.alpha = if (needsAbort) 1.0f else 0.5f
    }
    binding.gitLog.setOnClickListener {
      runCatching { launchActivity(GitLogActivity::class.java) }
        .onFailure { ex -> logcat(ERROR) { ex.asLog("Failed to start GitLogActivity") } }
    }
    binding.gitAbortRebase.setOnClickListener { abortRebase() }
    binding.gitResetToRemote.setOnClickListener { resetToRemote() }
    binding.gitGc.setOnClickListener {
      lifecycleScope.launch {
        launchGitOperation(GitOp.GC)
          .fold(
            success = ::finishOnSuccessHandler,
            failure = { err -> promptOnErrorHandler(err) { finish() } },
          )
      }
    }
  }

  private fun resetToRemote() {
    val dialog =
      TextInputDialog.newInstance(getString(R.string.git_utils_reset_remote_branch_title))
    dialog.show(supportFragmentManager, "BRANCH_INPUT_DIALOG")
    dialog.setFragmentResultListener(TextInputDialog.REQUEST_KEY) { _, bundle ->
      val result = bundle.getString(TextInputDialog.BUNDLE_KEY_TEXT)
      if (!result.isNullOrEmpty()) {
        remoteBranch = result
        lifecycleScope.launch {
          launchGitOperation(GitOp.RESET)
            .fold(
              success = ::finishOnSuccessHandler,
              failure = { err -> promptOnErrorHandler(err) { finish() } },
            )
        }
      }
    }
  }

  private fun abortRebase() {
    lifecycleScope.launch {
      launchGitOperation(GitOp.BREAK_OUT_OF_DETACHED)
        .fold(
          success = {
            val branch = PasswordRepository.getCurrentBranch()
            MaterialAlertDialogBuilder(this@GitConfigActivity).run {
              setTitle(resources.getString(R.string.git_abort_and_push_title))
              setMessage(
                resources.getString(
                  R.string.git_break_out_of_detached_success,
                  branch,
                  "conflicting-$branch-...",
                )
              )
              setOnDismissListener { finish() }
              setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ -> }
              show()
            }
          },
          failure = { err -> promptOnErrorHandler(err) { finish() } },
        )
    }
  }

  /**
   * Returns a user-friendly message about the current state of HEAD.
   *
   * The state is recognized to be either pointing to a branch or detached.
   */
  private fun headStatusMsg(repo: Repository): String {
    return runCatching {
        val headRef = repo.findRef(Constants.HEAD)
        if (headRef.isSymbolic) {
          val branchName = headRef.target.name
          val shortBranchName = Repository.shortenRefName(branchName)
          getString(R.string.git_head_on_branch, shortBranchName)
        } else {
          val commitHash = headRef.objectId.abbreviate(8).name()
          getString(R.string.git_head_detached, commitHash)
        }
      }
      .getOrElse { ex ->
        logcat(ERROR) { "Error getting HEAD reference\n${ex}" }
        getString(R.string.git_head_missing)
      }
  }
}

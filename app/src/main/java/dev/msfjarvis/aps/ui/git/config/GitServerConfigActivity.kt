/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.git.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.databinding.ActivityGitCloneBinding
import dev.msfjarvis.aps.ui.dialogs.BasicBottomSheet
import dev.msfjarvis.aps.ui.git.base.BaseGitActivity
import dev.msfjarvis.aps.util.extensions.snackbar
import dev.msfjarvis.aps.util.extensions.viewBinding
import dev.msfjarvis.aps.util.settings.AuthMode
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.Protocol
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

    newAuthMode = gitSettings.authMode

    binding.authModeGroup.apply {
      when (newAuthMode) {
        AuthMode.SshKey -> check(binding.authModeSshKey.id)
        AuthMode.Password -> check(binding.authModePassword.id)
        AuthMode.OpenKeychain -> check(binding.authModeOpenKeychain.id)
        AuthMode.None -> check(View.NO_ID)
      }
      setOnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
          binding.authModeSshKey.id -> newAuthMode = AuthMode.SshKey
          binding.authModeOpenKeychain.id -> newAuthMode = AuthMode.OpenKeychain
          binding.authModePassword.id -> newAuthMode = AuthMode.Password
          View.NO_ID -> newAuthMode = AuthMode.None
        }
      }
    }

    binding.serverUrl.setText(
      gitSettings.url.also {
        if (it.isNullOrEmpty()) return@also
        setAuthModes(it.startsWith("http://") || it.startsWith("https://"))
      }
    )
    binding.serverBranch.setText(gitSettings.branch)

    binding.serverUrl.doOnTextChanged { text, _, _, _ ->
      if (text.isNullOrEmpty()) return@doOnTextChanged
      setAuthModes(text.startsWith("http://") || text.startsWith("https://"))
    }

    binding.clearHostKeyButton.isVisible = gitSettings.hasSavedHostKey()
    binding.clearHostKeyButton.setOnClickListener {
      gitSettings.clearSavedHostKey()
      Snackbar.make(
          binding.root,
          getString(R.string.clear_saved_host_key_success),
          Snackbar.LENGTH_LONG
        )
        .show()
      it.isVisible = false
    }
    binding.saveButton.setOnClickListener {
      val newUrl = binding.serverUrl.text.toString().trim()
      // If url is of type john_doe@example.org:12435/path/to/repo, then not adding `ssh://`
      // in the beginning will cause the port to be seen as part of the path. Let users know
      // about it and offer a quickfix.
      if (newUrl.contains(PORT_REGEX)) {
        if (newUrl.startsWith("https://")) {
          BasicBottomSheet.Builder(this)
            .setTitleRes(R.string.https_scheme_with_port_title)
            .setMessageRes(R.string.https_scheme_with_port_message)
            .setPositiveButtonClickListener {
              binding.serverUrl.setText(newUrl.replace(PORT_REGEX, "/"))
            }
            .build()
            .show(supportFragmentManager, "SSH_SCHEME_WARNING")
          return@setOnClickListener
        } else if (!newUrl.startsWith("ssh://")) {
          BasicBottomSheet.Builder(this)
            .setTitleRes(R.string.ssh_scheme_needed_title)
            .setMessageRes(R.string.ssh_scheme_needed_message)
            .setPositiveButtonClickListener {
              @Suppress("SetTextI18n") binding.serverUrl.setText("ssh://$newUrl")
            }
            .build()
            .show(supportFragmentManager, "SSH_SCHEME_WARNING")
          return@setOnClickListener
        }
      }
      if (newUrl.startsWith("git://")) {
        BasicBottomSheet.Builder(this)
          .setTitleRes(R.string.git_scheme_disallowed_title)
          .setMessageRes(R.string.git_scheme_disallowed_message)
          .setPositiveButtonClickListener {}
          .build()
          .show(supportFragmentManager, "SSH_SCHEME_WARNING")
        return@setOnClickListener
      }
      when (val updateResult =
          gitSettings.updateConnectionSettingsIfValid(
            newAuthMode = newAuthMode,
            newUrl = binding.serverUrl.text.toString().trim(),
            newBranch = binding.serverBranch.text.toString().trim()
          )
      ) {
        GitSettings.UpdateConnectionSettingsResult.FailedToParseUrl -> {
          Snackbar.make(
              binding.root,
              getString(R.string.git_server_config_save_error),
              Snackbar.LENGTH_LONG
            )
            .show()
        }
        is GitSettings.UpdateConnectionSettingsResult.MissingUsername -> {
          when (updateResult.newProtocol) {
            Protocol.Https ->
              BasicBottomSheet.Builder(this)
                .setTitleRes(R.string.ssh_scheme_needed_title)
                .setMessageRes(R.string.git_server_config_save_missing_username_https)
                .setPositiveButtonClickListener {}
                .build()
                .show(supportFragmentManager, "HTTPS_MISSING_USERNAME")
            Protocol.Ssh ->
              BasicBottomSheet.Builder(this)
                .setTitleRes(R.string.ssh_scheme_needed_title)
                .setMessageRes(R.string.git_server_config_save_missing_username_ssh)
                .setPositiveButtonClickListener {}
                .build()
                .show(supportFragmentManager, "SSH_MISSING_USERNAME")
          }
        }
        GitSettings.UpdateConnectionSettingsResult.Valid -> {
          if (isClone && PasswordRepository.getRepository(null) == null)
            PasswordRepository.initialize()
          if (!isClone) {
            Snackbar.make(
                binding.root,
                getString(R.string.git_server_config_save_success),
                Snackbar.LENGTH_SHORT
              )
              .show()
            Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
          } else {
            cloneRepository()
          }
        }
        is GitSettings.UpdateConnectionSettingsResult.AuthModeMismatch -> {
          val message =
            getString(
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
        onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun setAuthModes(isHttps: Boolean) =
    with(binding) {
      if (isHttps) {
        authModeSshKey.isVisible = false
        authModeOpenKeychain.isVisible = false
        authModePassword.isVisible = true
        if (authModeGroup.checkedChipId != authModePassword.id) authModeGroup.check(View.NO_ID)
      } else {
        authModeSshKey.isVisible = true
        authModeOpenKeychain.isVisible = true
        authModePassword.isVisible = true
        if (authModeGroup.checkedChipId == View.NO_ID) authModeGroup.check(authModeSshKey.id)
      }
    }

  /** Clones the repository, the directory exists, deletes it */
  private fun cloneRepository() {
    val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory())
    val localDirFiles = localDir.listFiles() ?: emptyArray()
    // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
    if (localDir.exists() &&
        localDirFiles.isNotEmpty() &&
        !(localDirFiles.size == 1 && localDirFiles[0].name == ".git")
    ) {
      MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_delete_title)
        .setMessage(resources.getString(R.string.dialog_delete_msg, localDir.toString()))
        .setCancelable(false)
        .setPositiveButton(R.string.dialog_delete) { dialog, _ ->
          runCatching {
            lifecycleScope.launch {
              val snackbar =
                snackbar(
                  message = getString(R.string.delete_directory_progress_text),
                  length = Snackbar.LENGTH_INDEFINITE
                )
              withContext(Dispatchers.IO) { localDir.deleteRecursively() }
              snackbar.dismiss()
              launchGitOperation(GitOp.CLONE)
                .fold(
                  success = {
                    setResult(RESULT_OK)
                    finish()
                  },
                  failure = { err -> promptOnErrorHandler(err) { finish() } }
                )
            }
          }
            .onFailure { e ->
              e.printStackTrace()
              MaterialAlertDialogBuilder(this).setMessage(e.message).show()
            }
          dialog.cancel()
        }
        .setNegativeButton(R.string.dialog_do_not_delete) { dialog, _ -> dialog.cancel() }
        .show()
    } else {
      runCatching {
        // Silently delete & replace the lone .git folder if it exists
        if (localDir.exists() && localDirFiles.size == 1 && localDirFiles[0].name == ".git") {
          localDir.deleteRecursively()
        }
      }
        .onFailure { e ->
          e(e)
          MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }
      lifecycleScope.launch {
        launchGitOperation(GitOp.CLONE)
          .fold(
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

    private val PORT_REGEX = ":[0-9]{1,5}/".toRegex()

    fun createCloneIntent(context: Context): Intent {
      return Intent(context, GitServerConfigActivity::class.java).apply {
        putExtra("cloning", true)
      }
    }
  }
}

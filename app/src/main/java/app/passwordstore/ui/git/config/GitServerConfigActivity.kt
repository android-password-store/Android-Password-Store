/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.git.config

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
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.ActivityGitCloneBinding
import app.passwordstore.ui.dialogs.BasicBottomSheet
import app.passwordstore.ui.git.base.BaseGitActivity
import app.passwordstore.util.extensions.snackbar
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.AuthMode
import app.passwordstore.util.settings.GitSettings
import app.passwordstore.util.settings.Protocol
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

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
        AuthMode.None -> clearChecked()
      }
      addOnButtonCheckedListener { _, _, _ ->
        if (checkedButtonIds.isEmpty()) {
          newAuthMode = AuthMode.None
        } else {
          when (checkedButtonId) {
            binding.authModeSshKey.id -> newAuthMode = AuthMode.SshKey
            binding.authModePassword.id -> newAuthMode = AuthMode.Password
            View.NO_ID -> newAuthMode = AuthMode.None
          }
        }
      }
    }

    binding.serverUrl.setText(
      gitSettings.url.also {
        if (it.isNullOrEmpty()) return@also
        setAuthModes(it.startsWith("http://") || it.startsWith("https://"))
      }
    )

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
          Snackbar.LENGTH_LONG,
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
      when (
        val updateResult =
          gitSettings.updateConnectionSettingsIfValid(
            newAuthMode = newAuthMode,
            newUrl = binding.serverUrl.text.toString().trim(),
          )
      ) {
        GitSettings.UpdateConnectionSettingsResult.FailedToParseUrl -> {
          Snackbar.make(
              binding.root,
              getString(R.string.git_server_config_save_error),
              Snackbar.LENGTH_LONG,
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
          if (isClone && PasswordRepository.repository == null) PasswordRepository.initialize()
          if (!isClone) {
            Snackbar.make(
                binding.root,
                getString(R.string.git_server_config_save_success),
                Snackbar.LENGTH_SHORT,
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
        onBackPressedDispatcher.onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun setAuthModes(isHttps: Boolean) =
    with(binding) {
      if (isHttps) {
        authModeSshKey.isVisible = false
        authModePassword.isVisible = true
        if (authModeGroup.checkedButtonId != authModePassword.id) authModeGroup.clearChecked()
      } else {
        authModeSshKey.isVisible = true
        authModePassword.isVisible = true
        if (authModeGroup.checkedButtonId == View.NO_ID) authModeGroup.check(authModeSshKey.id)
      }
    }

  /** Clones the repository, the directory exists, deletes it */
  @OptIn(ExperimentalPathApi::class)
  private fun cloneRepository() {
    val localDir =
      requireNotNull(PasswordRepository.getRepositoryDirectory()) {
        "Repository directory must be set before cloning"
      }
    val localDirFiles = if (localDir.exists()) localDir.listDirectoryEntries() else listOf()
    // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
    if (
      localDirFiles.isNotEmpty() && !(localDirFiles.size == 1 && localDirFiles[0].name == ".git")
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
                    length = Snackbar.LENGTH_INDEFINITE,
                  )
                withContext(dispatcherProvider.io()) {
                  localDir.deleteRecursively()
                  localDir.createDirectories()
                }
                snackbar.dismiss()
                launchGitOperation(GitOp.CLONE)
                  .fold(
                    success = {
                      setResult(RESULT_OK)
                      finish()
                    },
                    failure = { err -> promptOnErrorHandler(err) { finish() } },
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
          logcat(ERROR) { e.asLog() }
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

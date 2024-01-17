/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.DecryptLayoutBinding
import app.passwordstore.ui.adapters.FieldItemAdapter
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.features.Feature.EnablePGPPassphraseCache
import app.passwordstore.util.features.Features
import app.passwordstore.util.settings.Constants
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

@AndroidEntryPoint
class DecryptActivity : BasePGPActivity() {

  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var passphraseCache: PGPPassphraseCache
  @Inject lateinit var features: Features

  private val binding by viewBinding(DecryptLayoutBinding::inflate)
  private val relativeParentPath by unsafeLazy { getParentPath(fullPath, repoPath) }
  private var passwordEntry: PasswordEntry? = null
  private var retries = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    title = name
    with(binding) {
      setContentView(root)
      passwordCategory.text = relativeParentPath
      passwordFile.text = name
      passwordFile.setOnLongClickListener {
        copyTextToClipboard(name)
        true
      }
    }
    if (
      features.isEnabled(EnablePGPPassphraseCache) &&
        BiometricAuthenticator.canAuthenticate(this@DecryptActivity)
    ) {
      BiometricAuthenticator.authenticate(
        this@DecryptActivity,
        R.string.biometric_prompt_title_gpg_passphrase_cache,
      ) { authResult ->
        requireKeysExist { decrypt(isError = false, authResult) }
      }
    } else {
      requireKeysExist { decrypt(isError = false, Result.CanceledByUser) }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.pgp_handler, menu)
    passwordEntry?.let { entry ->
      menu.findItem(R.id.edit_password).isVisible = true
      if (!entry.password.isNullOrBlank()) {
        menu.findItem(R.id.share_password_as_plaintext).isVisible = true
        menu.findItem(R.id.copy_password).isVisible = true
      }
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> onBackPressedDispatcher.onBackPressed()
      R.id.edit_password -> editPassword()
      R.id.share_password_as_plaintext -> shareAsPlaintext()
      R.id.copy_password -> copyPasswordToClipboard(passwordEntry?.password)
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  /**
   * Automatically finishes the activity after [PreferenceKeys.GENERAL_SHOW_TIME] seconds decryption
   * succeeded to prevent information leaks from stale activities.
   */
  private fun startAutoDismissTimer() {
    lifecycleScope.launch {
      val timeout =
        settings.getString(PreferenceKeys.GENERAL_SHOW_TIME)?.toIntOrNull()
          ?: Constants.DEFAULT_DECRYPTION_TIMEOUT
      if (timeout != 0) {
        delay(timeout.seconds)
        finish()
      }
    }
  }

  /**
   * Edit the current password and hide all the fields populated by encrypted data so that when the
   * result triggers they can be repopulated with new data.
   */
  private fun editPassword() {
    val intent = Intent(this, PasswordCreationActivity::class.java)
    intent.action = Intent.ACTION_VIEW
    intent.putExtra("FILE_PATH", relativeParentPath)
    intent.putExtra("REPO_PATH", repoPath)
    intent.putExtra(PasswordCreationActivity.EXTRA_FILE_NAME, name)
    intent.putExtra(PasswordCreationActivity.EXTRA_PASSWORD, passwordEntry?.password)
    intent.putExtra(PasswordCreationActivity.EXTRA_EXTRA_CONTENT, passwordEntry?.extraContentString)
    intent.putExtra(PasswordCreationActivity.EXTRA_EDITING, true)
    startActivity(intent)
    finish()
  }

  private fun shareAsPlaintext() {
    val sendIntent =
      Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
        type = "text/plain"
      }
    // Always show a picker to give the user a chance to cancel
    startActivity(
      Intent.createChooser(sendIntent, resources.getText(R.string.send_plaintext_password_to))
    )
  }

  private fun decrypt(isError: Boolean, authResult: Result) {
    val gpgIdentifiers = getPGPIdentifiers("") ?: return
    lifecycleScope.launch(dispatcherProvider.main()) {
      when (authResult) {
        // Internally handled by the prompt dialog
        is Result.Retry -> {}
        // If the dialog is dismissed for any reason, prompt for passphrase
        is Result.CanceledByUser,
        is Result.CanceledBySystem,
        is Result.Failure,
        is Result.HardwareUnavailableOrDisabled ->
          askPassphrase(isError, gpgIdentifiers, authResult)
        //
        is Result.Success -> {
          val cachedPassphrase =
            passphraseCache.retrieveCachedPassphrase(this@DecryptActivity, gpgIdentifiers.first())
          if (cachedPassphrase != null) {
            decryptWithCachedPassphrase(cachedPassphrase, gpgIdentifiers, authResult)
          } else {
            askPassphrase(isError, gpgIdentifiers, authResult)
          }
        }
      }
    }
  }

  private fun askPassphrase(
    isError: Boolean,
    gpgIdentifiers: List<PGPIdentifier>,
    authResult: Result,
  ) {
    if (retries < MAX_RETRIES) {
      retries += 1
    } else {
      finish()
    }
    val dialog = PasswordDialog()
    if (isError) {
      dialog.setError()
    }
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
    dialog.setFragmentResultListener(PasswordDialog.PASSWORD_RESULT_KEY) { key, bundle ->
      if (key == PasswordDialog.PASSWORD_RESULT_KEY) {
        val value = bundle.getString(PasswordDialog.PASSWORD_RESULT_KEY)!!
        lifecycleScope.launch(dispatcherProvider.main()) {
          when (val result = decryptWithPassphrase(value, gpgIdentifiers)) {
            is Ok -> {
              val entry = passwordEntryFactory.create(result.value.toByteArray())
              passwordEntry = entry
              createPasswordUI(entry)
              startAutoDismissTimer()
              if (authResult is Result.Success) {
                passphraseCache.cachePassphrase(this@DecryptActivity, gpgIdentifiers.first(), value)
              }
            }
            is Err -> {
              logcat(ERROR) { result.error.stackTraceToString() }
              askPassphrase(isError = true, gpgIdentifiers, authResult)
            }
          }
        }
      }
    }
  }

  private suspend fun decryptWithCachedPassphrase(
    passphrase: String,
    identifiers: List<PGPIdentifier>,
    authResult: Result,
  ) {
    when (val result = decryptWithPassphrase(passphrase, identifiers)) {
      is Ok -> {
        val entry = passwordEntryFactory.create(result.value.toByteArray())
        passwordEntry = entry
        createPasswordUI(entry)
        startAutoDismissTimer()
      }
      is Err -> {
        logcat(ERROR) { result.error.stackTraceToString() }
        decrypt(isError = true, authResult = authResult)
      }
    }
  }

  private suspend fun decryptWithPassphrase(
    password: String,
    gpgIdentifiers: List<PGPIdentifier>,
  ) = runCatching {
    val message = withContext(dispatcherProvider.io()) { File(fullPath).readBytes().inputStream() }
    val outputStream = ByteArrayOutputStream()
    val result =
      repository.decrypt(
        password,
        gpgIdentifiers,
        message,
        outputStream,
      )
    when (result) {
      is Ok -> outputStream
      is Err -> throw result.error
    }
  }

  private suspend fun createPasswordUI(entry: PasswordEntry) =
    withContext(dispatcherProvider.main()) {
      val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
      invalidateOptionsMenu()

      val items = arrayListOf<FieldItem>()
      if (!entry.password.isNullOrBlank()) {
        items.add(FieldItem.createPasswordField(entry.password!!))
        if (settings.getBoolean(PreferenceKeys.COPY_ON_DECRYPT, false)) {
          copyPasswordToClipboard(entry.password)
        }
      }

      if (entry.hasTotp()) {
        items.add(FieldItem.createOtpField(entry.totp.first()))
      }

      if (!entry.username.isNullOrBlank()) {
        items.add(FieldItem.createUsernameField(entry.username!!))
      }

      entry.extraContent.forEach { (key, value) ->
        items.add(FieldItem(key, value, FieldItem.ActionType.COPY))
      }

      val adapter = FieldItemAdapter(items, showPassword) { text -> copyTextToClipboard(text) }
      binding.recyclerView.adapter = adapter
      binding.recyclerView.itemAnimator = null

      if (entry.hasTotp()) {
        entry.totp.collect(adapter::updateOTPCode)
      }
    }

  private companion object {
    private const val MAX_RETRIES = 3
  }
}

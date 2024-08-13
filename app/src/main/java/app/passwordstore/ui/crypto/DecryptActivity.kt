/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import app.passwordstore.Application.Companion.screenWasOff
import app.passwordstore.R
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.crypto.errors.NonStandardAEAD
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.DecryptLayoutBinding
import app.passwordstore.ui.adapters.FieldItemAdapter
import app.passwordstore.ui.dialogs.BasicBottomSheet
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result as BiometricResult
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.features.Feature.EnablePGPPassphraseCache
import app.passwordstore.util.features.Features
import app.passwordstore.util.settings.Constants
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.onFailure
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
import logcat.asLog
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
  private var clearCache = true

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
      requireKeysExist { decrypt(isError = false, BiometricResult.CanceledByUser) }
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
    intent.putExtra(PasswordCreationActivity.EXTRA_USERNAME, passwordEntry?.username)
    intent.putExtra(PasswordCreationActivity.EXTRA_PASSWORD, passwordEntry?.password)
    intent.putExtra(
      PasswordCreationActivity.EXTRA_EXTRA_CONTENT,
      passwordEntry?.extraContentWithoutUsername,
    )
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

  private fun decrypt(isError: Boolean, authResult: BiometricResult) {
    val gpgIdentifiers = getPGPIdentifiers(relativeParentPath) ?: return
    lifecycleScope.launch(dispatcherProvider.main()) {
      when (authResult) {
        // Internally handled by the prompt dialog
        is BiometricResult.Retry -> {}
        // If the dialog is dismissed for any reason, prompt for passphrase
        is BiometricResult.CanceledByUser,
        is BiometricResult.CanceledBySystem,
        is BiometricResult.Failure,
        is BiometricResult.HardwareUnavailableOrDisabled ->
          askPassphrase(isError, gpgIdentifiers, authResult)
        is BiometricResult.Success -> {
          /* clear passphrase cache on first use after application startup or if screen was off;
          also make sure to purge a stale cache after caching has been disabled via PGP settings */
          clearCache = settings.getBoolean(PreferenceKeys.CLEAR_PASSPHRASE_CACHE, true)
          if (screenWasOff && clearCache) {
            passphraseCache.clearAllCachedPassphrases(this@DecryptActivity)
            screenWasOff = false
          }
          val cachedPassphrase =
            passphraseCache.retrieveCachedPassphrase(this@DecryptActivity, gpgIdentifiers.first())
          if (cachedPassphrase != null) {
            decryptWithPassphrase(cachedPassphrase, gpgIdentifiers, authResult)
          } else {
            askPassphrase(isError, gpgIdentifiers, authResult)
          }
        }
      }
    }
  }

  private suspend fun askPassphrase(
    isError: Boolean,
    gpgIdentifiers: List<PGPIdentifier>,
    authResult: BiometricResult,
  ) {
    if (retries < MAX_RETRIES) {
      retries += 1
    } else {
      finish()
    }
    if (!repository.isPasswordProtected(gpgIdentifiers)) {
      decryptWithPassphrase(passphrase = "", gpgIdentifiers, authResult)
      return
    }
    val dialog =
      PasswordDialog.newInstance(
        cacheEnabled = features.isEnabled(EnablePGPPassphraseCache),
        clearCache = clearCache,
      )
    if (isError) {
      dialog.setError()
    }
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
    dialog.setFragmentResultListener(PasswordDialog.PASSWORD_RESULT_KEY) { key, bundle ->
      if (key == PasswordDialog.PASSWORD_RESULT_KEY) {
        val passphrase = bundle.getString(PasswordDialog.PASSWORD_PHRASE_KEY)!!
        clearCache = bundle.getBoolean(PasswordDialog.PASSWORD_CLEAR_KEY)
        lifecycleScope.launch(dispatcherProvider.main()) {
          decryptWithPassphrase(passphrase, gpgIdentifiers, authResult) {
            runCatching {
                if (authResult is BiometricResult.Success) {
                  passphraseCache.cachePassphrase(
                    this@DecryptActivity,
                    gpgIdentifiers.first(),
                    passphrase,
                  )
                  settings.edit { putBoolean(PreferenceKeys.CLEAR_PASSPHRASE_CACHE, clearCache) }
                }
              }
              .onFailure { e -> logcat { e.asLog() } }
          }
        }
      }
    }
  }

  private suspend fun decryptWithPassphrase(
    passphrase: String,
    identifiers: List<PGPIdentifier>,
    authResult: BiometricResult,
    onSuccess: suspend () -> Unit = {},
  ) {
    val message = withContext(dispatcherProvider.io()) { File(fullPath).readBytes().inputStream() }
    val outputStream = ByteArrayOutputStream()
    val result = repository.decrypt(passphrase, identifiers, message, outputStream)
    if (result.isOk) {
      val entry = passwordEntryFactory.create(result.value.toByteArray())
      passwordEntry = entry
      createPasswordUI(entry)
      startAutoDismissTimer()
      onSuccess()
    } else {
      logcat(ERROR) { result.error.stackTraceToString() }
      when (result.error) {
        is NonStandardAEAD -> {
          BasicBottomSheet.Builder(this)
            .setTitle(getString(R.string.aead_detect_title))
            .setMessage(getString(R.string.aead_detect_message, result.error.message))
            .setPositiveButtonClickListener(getString(R.string.dialog_ok)) {
              setResult(RESULT_CANCELED)
              finish()
            }
            .setOnDismissListener {
              setResult(RESULT_CANCELED)
              finish()
            }
            .build()
            .show(supportFragmentManager, "AEAD_INFO_SHEET")
        }
        else -> decrypt(isError = true, authResult = authResult)
      }
    }
  }

  private suspend fun createPasswordUI(entry: PasswordEntry) =
    withContext(dispatcherProvider.main()) {
      val labelFormat = resources.getString(R.string.otp_label_format)
      val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
      invalidateOptionsMenu()

      val items = arrayListOf<FieldItem>()
      if (!entry.password.isNullOrBlank()) {
        items.add(FieldItem.createPasswordField(getString(R.string.password), entry.password!!))
        if (settings.getBoolean(PreferenceKeys.COPY_ON_DECRYPT, false)) {
          copyPasswordToClipboard(entry.password)
        }
      }

      if (entry.hasTotp()) {
        items.add(FieldItem.createOtpField(labelFormat, entry.totp.first()))
      }

      if (!entry.username.isNullOrBlank()) {
        items.add(FieldItem.createUsernameField(getString(R.string.username), entry.username!!))
      }

      entry.extraContent.forEach { (key, value) ->
        items.add(FieldItem.createFreeformField(key, value))
      }

      val adapter = FieldItemAdapter(items, showPassword) { text -> copyTextToClipboard(text) }
      binding.recyclerView.adapter = adapter
      binding.recyclerView.itemAnimator = null

      if (entry.hasTotp()) {
        lifecycleScope.launch { entry.totp.collect { adapter.updateOTPCode(it, labelFormat) } }
      }
    }

  private companion object {
    private const val MAX_RETRIES = 3
  }
}

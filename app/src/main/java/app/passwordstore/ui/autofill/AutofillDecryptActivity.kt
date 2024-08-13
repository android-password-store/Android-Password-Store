/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.core.content.edit
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import app.passwordstore.Application.Companion.screenWasOff
import app.passwordstore.R
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.crypto.BasePGPActivity
import app.passwordstore.ui.crypto.PasswordDialog
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result as BiometricResult
import app.passwordstore.util.autofill.AutofillPreferences
import app.passwordstore.util.autofill.AutofillResponseBuilder
import app.passwordstore.util.extensions.asLog
import app.passwordstore.util.features.Feature.EnablePGPPassphraseCache
import app.passwordstore.util.features.Features
import app.passwordstore.util.settings.DirectoryStructure
import app.passwordstore.util.settings.PreferenceKeys
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@AndroidEntryPoint
class AutofillDecryptActivity : BasePGPActivity() {

  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var features: Features
  @Inject lateinit var passphraseCache: PGPPassphraseCache

  private lateinit var directoryStructure: DirectoryStructure
  private var clearCache = true

  override fun onStart() {
    super.onStart()
    val filePath =
      intent?.getStringExtra(EXTRA_FILE_PATH)
        ?: run {
          logcat(ERROR) { "AutofillDecryptActivity started without EXTRA_FILE_PATH" }
          finish()
          return
        }
    val clientState =
      intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE)
        ?: run {
          logcat(ERROR) { "AutofillDecryptActivity started without EXTRA_CLIENT_STATE" }
          finish()
          return
        }
    val isSearchAction = intent?.getBooleanExtra(EXTRA_SEARCH_ACTION, true)!!
    val action = if (isSearchAction) AutofillAction.Search else AutofillAction.Match
    directoryStructure = AutofillPreferences.directoryStructure(this)
    logcat { action.toString() }
    requireKeysExist {
      if (
        features.isEnabled(EnablePGPPassphraseCache) && BiometricAuthenticator.canAuthenticate(this)
      ) {
        BiometricAuthenticator.authenticate(
          this,
          R.string.biometric_prompt_title_gpg_passphrase_cache,
        ) { authResult ->
          decrypt(filePath, clientState, action, authResult)
        }
      } else {
        decrypt(filePath, clientState, action, BiometricResult.CanceledByUser)
      }
    }
  }

  private fun decrypt(
    filePath: String,
    clientState: Bundle,
    action: AutofillAction,
    authResult: BiometricResult,
  ) {
    val gpgIdentifiers =
      getPGPIdentifiers(
        getParentPath(filePath, PasswordRepository.getRepositoryDirectory().toString())
      ) ?: return
    lifecycleScope.launch(dispatcherProvider.main()) {
      when (authResult) {
        // Internally handled by the prompt dialog
        is BiometricResult.Retry -> {}
        // If the dialog is dismissed for any reason, prompt for passphrase
        is BiometricResult.CanceledBySystem,
        is BiometricResult.CanceledByUser,
        is BiometricResult.Failure,
        is BiometricResult.HardwareUnavailableOrDisabled ->
          askPassphrase(filePath, gpgIdentifiers, clientState, action)
        is BiometricResult.Success -> {
          /* clear passphrase cache on first use after application startup or if screen was off;
          also make sure to purge a stale cache after caching has been disabled via PGP settings */
          clearCache = settings.getBoolean(PreferenceKeys.CLEAR_PASSPHRASE_CACHE, true)
          if (screenWasOff && clearCache) {
            passphraseCache.clearAllCachedPassphrases(this@AutofillDecryptActivity)
            screenWasOff = false
          }
          val cachedPassphrase =
            passphraseCache.retrieveCachedPassphrase(
              this@AutofillDecryptActivity,
              gpgIdentifiers.first(),
            )
          if (cachedPassphrase != null) {
            decryptWithPassphrase(
              File(filePath),
              gpgIdentifiers,
              clientState,
              action,
              cachedPassphrase,
            )
          } else {
            askPassphrase(filePath, gpgIdentifiers, clientState, action)
          }
        }
      }
    }
  }

  private suspend fun askPassphrase(
    filePath: String,
    identifiers: List<PGPIdentifier>,
    clientState: Bundle,
    action: AutofillAction,
  ) {
    if (!repository.isPasswordProtected(identifiers)) {
      decryptWithPassphrase(File(filePath), identifiers, clientState, action, password = "")
      return
    }
    val dialog =
      PasswordDialog.newInstance(
        cacheEnabled = features.isEnabled(EnablePGPPassphraseCache),
        clearCache = clearCache,
      )
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
    dialog.setFragmentResultListener(PasswordDialog.PASSWORD_RESULT_KEY) { key, bundle ->
      if (key == PasswordDialog.PASSWORD_RESULT_KEY) {
        val value = bundle.getString(PasswordDialog.PASSWORD_PHRASE_KEY)!!
        clearCache = bundle.getBoolean(PasswordDialog.PASSWORD_CLEAR_KEY)
        lifecycleScope.launch(dispatcherProvider.main()) {
          decryptWithPassphrase(File(filePath), identifiers, clientState, action, value)
        }
      }
    }
  }

  private suspend fun decryptWithPassphrase(
    filePath: File,
    identifiers: List<PGPIdentifier>,
    clientState: Bundle,
    action: AutofillAction,
    password: String,
  ) {
    val credentials = decryptCredential(filePath, password, identifiers)
    if (credentials == null) {
      setResult(RESULT_CANCELED)
    } else {
      val fillInDataset =
        AutofillResponseBuilder.makeFillInDataset(
          this@AutofillDecryptActivity,
          credentials,
          clientState,
          action,
        )
      withContext(dispatcherProvider.main()) {
        setResult(
          RESULT_OK,
          Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset) },
        )
      }
    }
    withContext(dispatcherProvider.main()) { finish() }
  }

  private suspend fun decryptCredential(
    file: File,
    password: String,
    identifiers: List<PGPIdentifier>,
  ): Credentials? {
    runCatching { file.readBytes().inputStream() }
      .onFailure { e ->
        logcat(ERROR) { e.asLog("File to decrypt not found") }
        return null
      }
      .onSuccess { encryptedInput ->
        val outputStream = ByteArrayOutputStream()
        repository
          .decrypt(password, identifiers, encryptedInput, outputStream)
          .onFailure { e ->
            logcat(ERROR) { e.asLog("Decryption failed") }
            return null
          }
          .onSuccess { result ->
            return runCatching {
                runCatching {
                    if (features.isEnabled(EnablePGPPassphraseCache)) {
                      passphraseCache.cachePassphrase(this, identifiers.first(), password)
                      settings.edit {
                        putBoolean(PreferenceKeys.CLEAR_PASSPHRASE_CACHE, clearCache)
                      }
                    }
                  }
                  .onFailure { e -> logcat { e.asLog() } }
                val entry = passwordEntryFactory.create(result.toByteArray())
                AutofillPreferences.credentialsFromStoreEntry(this, file, entry, directoryStructure)
              }
              .getOrElse { e ->
                logcat(ERROR) { e.asLog("Failed to parse password entry") }
                return null
              }
          }
      }
    return null
  }

  companion object {

    private const val EXTRA_FILE_PATH = "app.passwordstore.autofill.oreo.EXTRA_FILE_PATH"
    private const val EXTRA_SEARCH_ACTION = "app.passwordstore.autofill.oreo.EXTRA_SEARCH_ACTION"

    private var decryptFileRequestCode = 1

    fun makeDecryptFileIntent(file: File, forwardedExtras: Bundle, context: Context): Intent {
      return Intent(context, AutofillDecryptActivity::class.java).apply {
        putExtras(forwardedExtras)
        putExtra(EXTRA_SEARCH_ACTION, true)
        putExtra(EXTRA_FILE_PATH, file.absolutePath)
      }
    }

    fun makeDecryptFileIntentSender(file: File, context: Context): IntentSender {
      val intent =
        Intent(context, AutofillDecryptActivity::class.java).apply {
          putExtra(EXTRA_SEARCH_ACTION, false)
          putExtra(EXTRA_FILE_PATH, file.absolutePath)
        }
      return PendingIntent.getActivity(
          context,
          decryptFileRequestCode++,
          intent,
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
          } else {
            PendingIntent.FLAG_CANCEL_CURRENT
          },
        )
        .intentSender
    }
  }
}

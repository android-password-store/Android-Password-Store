/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
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
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.ui.crypto.BasePGPActivity
import app.passwordstore.ui.crypto.PasswordDialog
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result
import app.passwordstore.util.autofill.AutofillPreferences
import app.passwordstore.util.autofill.AutofillResponseBuilder
import app.passwordstore.util.autofill.DirectoryStructure
import app.passwordstore.util.extensions.asLog
import app.passwordstore.util.features.Feature.EnablePGPPassphraseCache
import app.passwordstore.util.features.Features
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
import logcat.logcat

@AndroidEntryPoint
class AutofillDecryptActivity : BasePGPActivity() {

  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var features: Features
  @Inject lateinit var passphraseCache: PGPPassphraseCache

  private lateinit var directoryStructure: DirectoryStructure

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
        decrypt(filePath, clientState, action, Result.Cancelled)
      }
    }
  }

  private fun decrypt(
    filePath: String,
    clientState: Bundle,
    action: AutofillAction,
    authResult: Result,
  ) {
    val gpgIdentifiers = getPGPIdentifiers("") ?: return
    lifecycleScope.launch(dispatcherProvider.main()) {
      when (authResult) {
        // Internally handled by the prompt dialog
        is Result.Retry -> {}
        // If the dialog is dismissed for any reason, prompt for passphrase
        is Result.Cancelled,
        is Result.Failure,
        is Result.HardwareUnavailableOrDisabled -> askPassphrase(filePath, clientState, action)
        //
        is Result.Success -> {
          val cachedPassphrase =
            passphraseCache.retrieveCachedPassphrase(
              this@AutofillDecryptActivity,
              gpgIdentifiers.first()
            )
          if (cachedPassphrase != null) {
            decryptWithPassphrase(File(filePath), clientState, action, cachedPassphrase)
          } else {
            askPassphrase(filePath, clientState, action)
          }
        }
      }
    }
  }

  private fun askPassphrase(filePath: String, clientState: Bundle, action: AutofillAction) {
    val dialog = PasswordDialog()
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
    dialog.setFragmentResultListener(PasswordDialog.PASSWORD_RESULT_KEY) { key, bundle ->
      if (key == PasswordDialog.PASSWORD_RESULT_KEY) {
        val value = bundle.getString(PasswordDialog.PASSWORD_RESULT_KEY)!!
        lifecycleScope.launch(dispatcherProvider.main()) {
          decryptWithPassphrase(File(filePath), clientState, action, value)
        }
      }
    }
  }

  private suspend fun decryptWithPassphrase(
    filePath: File,
    clientState: Bundle,
    action: AutofillAction,
    password: String,
  ) {
    val credentials = decryptCredential(filePath, password)
    if (credentials == null) {
      setResult(RESULT_CANCELED)
    } else {
      val fillInDataset =
        AutofillResponseBuilder.makeFillInDataset(
          this@AutofillDecryptActivity,
          credentials,
          clientState,
          action
        )
      withContext(dispatcherProvider.main()) {
        setResult(
          RESULT_OK,
          Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset) }
        )
      }
    }
    withContext(dispatcherProvider.main()) { finish() }
  }

  private suspend fun decryptCredential(file: File, password: String): Credentials? {
    val gpgIdentifiers = getPGPIdentifiers("") ?: return null
    runCatching { file.readBytes().inputStream() }
      .onFailure { e ->
        logcat(ERROR) { e.asLog("File to decrypt not found") }
        return null
      }
      .onSuccess { encryptedInput ->
        runCatching {
            withContext(dispatcherProvider.io()) {
              val outputStream = ByteArrayOutputStream()
              repository.decrypt(
                password,
                gpgIdentifiers,
                encryptedInput,
                outputStream,
              )
              outputStream
            }
          }
          .onFailure { e ->
            logcat(ERROR) { e.asLog("Decryption failed") }
            return null
          }
          .onSuccess { result ->
            return runCatching {
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

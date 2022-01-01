/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.data.crypto.CryptoRepository
import dev.msfjarvis.aps.data.passfile.PasswordEntry
import dev.msfjarvis.aps.ui.crypto.DecryptActivityV2
import dev.msfjarvis.aps.util.autofill.AutofillPreferences
import dev.msfjarvis.aps.util.autofill.AutofillResponseBuilder
import dev.msfjarvis.aps.util.autofill.DirectoryStructure
import dev.msfjarvis.aps.util.extensions.asLog
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class AutofillDecryptActivityV2 : AppCompatActivity() {

  companion object {

    private const val EXTRA_FILE_PATH = "dev.msfjarvis.aps.autofill.oreo.EXTRA_FILE_PATH"
    private const val EXTRA_SEARCH_ACTION = "dev.msfjarvis.aps.autofill.oreo.EXTRA_SEARCH_ACTION"

    private var decryptFileRequestCode = 1

    fun makeDecryptFileIntent(file: File, forwardedExtras: Bundle, context: Context): Intent {
      return Intent(context, AutofillDecryptActivityV2::class.java).apply {
        putExtras(forwardedExtras)
        putExtra(EXTRA_SEARCH_ACTION, true)
        putExtra(EXTRA_FILE_PATH, file.absolutePath)
      }
    }

    fun makeDecryptFileIntentSender(file: File, context: Context): IntentSender {
      val intent =
        Intent(context, AutofillDecryptActivityV2::class.java).apply {
          putExtra(EXTRA_SEARCH_ACTION, false)
          putExtra(EXTRA_FILE_PATH, file.absolutePath)
        }
      return PendingIntent.getActivity(
          context,
          decryptFileRequestCode++,
          intent,
          PendingIntent.FLAG_CANCEL_CURRENT
        )
        .intentSender
    }
  }

  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var repository: CryptoRepository

  private lateinit var directoryStructure: DirectoryStructure

  override fun onStart() {
    super.onStart()
    val filePath =
      intent?.getStringExtra(EXTRA_FILE_PATH)
        ?: run {
          logcat(ERROR) { "AutofillDecryptActivityV2 started without EXTRA_FILE_PATH" }
          finish()
          return
        }
    val clientState =
      intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE)
        ?: run {
          logcat(ERROR) { "AutofillDecryptActivityV2 started without EXTRA_CLIENT_STATE" }
          finish()
          return
        }
    val isSearchAction = intent?.getBooleanExtra(EXTRA_SEARCH_ACTION, true)!!
    val action = if (isSearchAction) AutofillAction.Search else AutofillAction.Match
    directoryStructure = AutofillPreferences.directoryStructure(this)
    logcat { action.toString() }
    lifecycleScope.launch {
      val credentials = decryptCredential(File(filePath))
      if (credentials == null) {
        setResult(RESULT_CANCELED)
      } else {
        val fillInDataset =
          AutofillResponseBuilder.makeFillInDataset(
            this@AutofillDecryptActivityV2,
            credentials,
            clientState,
            action
          )
        withContext(Dispatchers.Main) {
          setResult(
            RESULT_OK,
            Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset) }
          )
        }
      }
      withContext(Dispatchers.Main) { finish() }
    }
  }

  private suspend fun decryptCredential(file: File): Credentials? {
    runCatching { file.readBytes().inputStream() }
      .onFailure { e ->
        logcat(ERROR) { e.asLog("File to decrypt not found") }
        return null
      }
      .onSuccess { encryptedInput ->
        runCatching {
          withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            repository.decrypt(
              DecryptActivityV2.PASS,
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
              val entry = passwordEntryFactory.create(lifecycleScope, result.toByteArray())
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
}

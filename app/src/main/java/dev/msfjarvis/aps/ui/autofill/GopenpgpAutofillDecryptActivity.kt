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
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.data.crypto.GopenpgpCryptoHandler
import dev.msfjarvis.aps.injection.password.PasswordEntryFactory
import dev.msfjarvis.aps.ui.crypto.GopenpgpDecryptActivity
import dev.msfjarvis.aps.util.autofill.AutofillPreferences
import dev.msfjarvis.aps.util.autofill.AutofillResponseBuilder
import dev.msfjarvis.aps.util.autofill.DirectoryStructure
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class GopenpgpAutofillDecryptActivity : AppCompatActivity() {

  companion object {

    private const val EXTRA_FILE_PATH = "dev.msfjarvis.aps.autofill.oreo.EXTRA_FILE_PATH"
    private const val EXTRA_SEARCH_ACTION = "dev.msfjarvis.aps.autofill.oreo.EXTRA_SEARCH_ACTION"

    private var decryptFileRequestCode = 1

    fun makeDecryptFileIntent(file: File, forwardedExtras: Bundle, context: Context): Intent {
      return Intent(context, GopenpgpAutofillDecryptActivity::class.java).apply {
        putExtras(forwardedExtras)
        putExtra(EXTRA_SEARCH_ACTION, true)
        putExtra(EXTRA_FILE_PATH, file.absolutePath)
      }
    }

    fun makeDecryptFileIntentSender(file: File, context: Context): IntentSender {
      val intent =
        Intent(context, GopenpgpAutofillDecryptActivity::class.java).apply {
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

  @Inject lateinit var passwordEntryFactory: PasswordEntryFactory
  @Inject lateinit var gopenpgpCrypto: GopenpgpCryptoHandler

  private lateinit var directoryStructure: DirectoryStructure

  override fun onStart() {
    super.onStart()
    val filePath =
      intent?.getStringExtra(EXTRA_FILE_PATH)
        ?: run {
          e { "GopenpgpAutofillDecryptActivity started without EXTRA_FILE_PATH" }
          finish()
          return
        }
    val clientState =
      intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE)
        ?: run {
          e { "GopenpgpAutofillDecryptActivity started without EXTRA_CLIENT_STATE" }
          finish()
          return
        }
    val isSearchAction = intent?.getBooleanExtra(EXTRA_SEARCH_ACTION, true)!!
    val action = if (isSearchAction) AutofillAction.Search else AutofillAction.Match
    directoryStructure = AutofillPreferences.directoryStructure(this)
    d { action.toString() }
    lifecycleScope.launch {
      val credentials = decryptCredential(File(filePath))
      if (credentials == null) {
        setResult(RESULT_CANCELED)
      } else {
        val fillInDataset =
          AutofillResponseBuilder.makeFillInDataset(
            this@GopenpgpAutofillDecryptActivity,
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
    runCatching { file.inputStream() }
      .onFailure { e ->
        e(e) { "File to decrypt not found" }
        return null
      }
      .onSuccess { encryptedInput ->
        runCatching {
          withContext(Dispatchers.IO) {
            gopenpgpCrypto.decrypt(
              GopenpgpDecryptActivity.PRIV_KEY,
              GopenpgpDecryptActivity.PASS.toByteArray(charset = Charsets.UTF_8),
              encryptedInput.readBytes()
            )
          }
        }
          .onFailure { e ->
            e(e) { "Decryption with Gopenpgp failed" }
            return null
          }
          .onSuccess { result ->
            return runCatching {
              val entry = passwordEntryFactory.create(lifecycleScope, result)
              AutofillPreferences.credentialsFromStoreEntry(this, file, entry, directoryStructure)
            }
              .getOrElse { e ->
                e(e) { "Failed to parse password entry" }
                return null
              }
          }
      }
    return null
  }
}

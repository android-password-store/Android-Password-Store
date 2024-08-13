/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("BlockingMethodInNonBlockingContext")

package app.passwordstore.ui.pgp

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.appcompat.app.AppCompatActivity
import app.passwordstore.R
import app.passwordstore.crypto.KeyUtils.tryGetId
import app.passwordstore.crypto.PGPKey
import app.passwordstore.crypto.PGPKeyManager
import app.passwordstore.crypto.errors.KeyAlreadyExistsException
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class PGPKeyImportActivity : AppCompatActivity() {

  /**
   * A [ByteArray] containing the contents of the previously selected file. This is necessary for
   * the replacement case where we do not want users to have to pick the file again.
   */
  private var lastBytes: ByteArray? = null
  @Inject lateinit var keyManager: PGPKeyManager

  private val pgpKeyImportAction =
    registerForActivityResult(OpenDocument()) { uri ->
      runCatching {
          if (uri == null) {
            return@runCatching null
          }
          val keyInputStream =
            contentResolver.openInputStream(uri)
              ?: throw IllegalStateException("Failed to open selected file")
          val bytes = keyInputStream.use { `is` -> `is`.readBytes() }
          importKey(bytes, false)
        }
        .run(::handleImportResult)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pgpKeyImportAction.launch(arrayOf("*/*"))
  }

  override fun onDestroy() {
    lastBytes = null
    super.onDestroy()
  }

  private fun importKey(bytes: ByteArray, replace: Boolean): PGPKey? {
    lastBytes = bytes
    val (key, error) = runBlocking { keyManager.addKey(PGPKey(bytes), replace = replace) }
    if (replace) {
      lastBytes = null
    }
    if (error != null) throw error
    return key
  }

  private fun handleImportResult(result: Result<PGPKey?, Throwable>) {
    if (result.isOk) {
      val key = result.value
      if (key == null) {
        setResult(RESULT_CANCELED)
        finish()
        // This return convinces Kotlin that the control flow for `key == null` definitely
        // terminates here and allows for a smart cast below.
        return
      }
      MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.pgp_key_import_succeeded))
        .setMessage(getString(R.string.pgp_key_import_succeeded_message, tryGetId(key)))
        .setPositiveButton(android.R.string.ok) { _, _ ->
          setResult(RESULT_OK)
          finish()
        }
        .setCancelable(false)
        .show()
    } else {
      if (result.error is KeyAlreadyExistsException && lastBytes != null) {
        MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.pgp_key_import_failed))
          .setMessage(getString(R.string.pgp_key_import_failed_replace_message))
          .setPositiveButton(R.string.dialog_yes) { _, _ ->
            handleImportResult(runCatching { importKey(lastBytes!!, replace = true) })
          }
          .setNegativeButton(R.string.dialog_no) { _, _ -> finish() }
          .setCancelable(false)
          .show()
      } else {
        MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.pgp_key_import_failed))
          .setMessage(result.error.message)
          .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
          .setCancelable(false)
          .show()
      }
    }
  }
}

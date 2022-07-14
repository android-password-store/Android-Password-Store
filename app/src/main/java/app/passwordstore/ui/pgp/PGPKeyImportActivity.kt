/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
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
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class PGPKeyImportActivity : AppCompatActivity() {

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
          val bytes = keyInputStream.readBytes()
          val (key, error) = runBlocking { keyManager.addKey(PGPKey(bytes)) }
          if (error != null) throw error
          key
        }
        .mapBoth(
          { key ->
            if (key != null) {
              MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.pgp_key_import_succeeded))
                .setMessage(getString(R.string.pgp_key_import_succeeded_message, tryGetId(key)))
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            } else {
              finish()
            }
          },
          { throwable ->
            MaterialAlertDialogBuilder(this)
              .setTitle(getString(R.string.pgp_key_import_failed))
              .setMessage(throwable.message)
              .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
              .setOnCancelListener { finish() }
              .show()
          }
        )
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pgpKeyImportAction.launch(arrayOf("*/*"))
  }
}

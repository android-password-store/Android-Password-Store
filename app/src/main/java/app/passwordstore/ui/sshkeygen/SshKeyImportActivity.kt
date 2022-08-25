/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.sshkeygen

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.util.git.sshj.SshKey
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.ssh.SSHKeyManager
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SshKeyImportActivity : AppCompatActivity() {

  @Inject lateinit var sshKeyManager: SSHKeyManager

  private val sshKeyImportAction =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri == null) {
        finish()
        return@registerForActivityResult
      }
      runCatching {
          lifecycleScope.launch {
            sshKeyManager.importKey(uri)
            Toast.makeText(
              this@SshKeyImportActivity,
              resources.getString(R.string.ssh_key_success_dialog_title),
              Toast.LENGTH_LONG
            )
              .show()
            setResult(RESULT_OK)
            finish()
          }
        }
        .onFailure { e ->
          MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.ssh_key_error_dialog_title))
            .setMessage(e.message)
            .setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ -> finish() }
            .show()
        }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launchWhenStarted {
      if (sshKeyManager.keyExists()) {
        MaterialAlertDialogBuilder(this@SshKeyImportActivity).run {
          setTitle(R.string.ssh_keygen_existing_title)
          setMessage(R.string.ssh_keygen_existing_message)
          setPositiveButton(R.string.ssh_keygen_existing_replace) { _, _ -> importSshKey() }
          setNegativeButton(R.string.ssh_keygen_existing_keep) { _, _ -> finish() }
          setOnCancelListener { finish() }
          show()
        }
      } else {
        importSshKey()
      }
    }
  }

  private fun importSshKey() {
    sshKeyImportAction.launch(arrayOf("*/*"))
  }
}

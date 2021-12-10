/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.passfile.PasswordEntry
import dev.msfjarvis.aps.data.password.FieldItem
import dev.msfjarvis.aps.databinding.DecryptLayoutBinding
import dev.msfjarvis.aps.injection.crypto.CryptoSet
import dev.msfjarvis.aps.ui.adapters.FieldItemAdapter
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import dev.msfjarvis.aps.util.extensions.viewBinding
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class DecryptActivityV2 : BasePgpActivity() {

  private val binding by viewBinding(DecryptLayoutBinding::inflate)
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var cryptos: CryptoSet
  private val relativeParentPath by unsafeLazy { getParentPath(fullPath, repoPath) }

  private var passwordEntry: PasswordEntry? = null

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
    decrypt()
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
      android.R.id.home -> onBackPressed()
      R.id.edit_password -> editPassword()
      R.id.share_password_as_plaintext -> shareAsPlaintext()
      R.id.copy_password -> copyPasswordToClipboard(passwordEntry?.password)
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  /**
   * Automatically finishes the activity 60 seconds after decryption succeeded to prevent
   * information leaks from stale activities.
   */
  @OptIn(ExperimentalTime::class)
  private fun startAutoDismissTimer() {
    lifecycleScope.launch {
      delay(Duration.seconds(60))
      finish()
    }
  }

  /**
   * Edit the current password and hide all the fields populated by encrypted data so that when the
   * result triggers they can be repopulated with new data.
   */
  private fun editPassword() {
    val intent = Intent(this, PasswordCreationActivityV2::class.java)
    intent.putExtra("FILE_PATH", relativeParentPath)
    intent.putExtra("REPO_PATH", repoPath)
    intent.putExtra(PasswordCreationActivityV2.EXTRA_FILE_NAME, name)
    intent.putExtra(PasswordCreationActivityV2.EXTRA_PASSWORD, passwordEntry?.password)
    intent.putExtra(
      PasswordCreationActivityV2.EXTRA_EXTRA_CONTENT,
      passwordEntry?.extraContentString
    )
    intent.putExtra(PasswordCreationActivityV2.EXTRA_EDITING, true)
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

  private fun decrypt() {
    lifecycleScope.launch {
      // TODO(msfjarvis): native methods are fallible, add error handling once out of testing
      val message = withContext(Dispatchers.IO) { File(fullPath).inputStream() }
      val result =
        withContext(Dispatchers.IO) {
          val crypto = cryptos.first { it.canHandle(fullPath) }
          val outputStream = ByteArrayOutputStream()
          crypto.decrypt(
            PRIV_KEY,
            PASS,
            message,
            outputStream,
          )
          outputStream
        }
      startAutoDismissTimer()

      val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
      val entry = passwordEntryFactory.create(lifecycleScope, result.toByteArray())
      passwordEntry = entry
      invalidateOptionsMenu()

      val items = arrayListOf<FieldItem>()
      if (!entry.password.isNullOrBlank()) {
        items.add(FieldItem.createPasswordField(entry.password!!))
      }

      if (entry.hasTotp()) {
        items.add(FieldItem.createOtpField(entry.totp.value))
      }

      if (!entry.username.isNullOrBlank()) {
        items.add(FieldItem.createUsernameField(entry.username!!))
      }

      entry.extraContent.forEach { (key, value) ->
        items.add(FieldItem(key, value, FieldItem.ActionType.COPY))
      }

      val adapter = FieldItemAdapter(items, showPassword) { text -> copyTextToClipboard(text) }
      binding.recyclerView.adapter = adapter

      if (entry.hasTotp()) {
        lifecycleScope.launch {
          entry
            .totp
            .onEach { code -> withContext(Dispatchers.Main) { adapter.updateOTPCode(code) } }
            .collect()
        }
      }
    }
  }

  companion object {
    // TODO(msfjarvis): source these from storage and user input
    const val PRIV_KEY = ""
    const val PASS = ""
  }
}

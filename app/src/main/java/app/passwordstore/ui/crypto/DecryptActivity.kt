/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.crypto.CryptoRepository
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.DecryptLayoutBinding
import app.passwordstore.ui.adapters.FieldItemAdapter
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.isErr
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrapError
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

@OptIn(ExperimentalTime::class)
@AndroidEntryPoint
class DecryptActivity : BasePgpActivity() {

  private val binding by viewBinding(DecryptLayoutBinding::inflate)
  private val relativeParentPath by unsafeLazy { getParentPath(fullPath, repoPath) }
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var repository: CryptoRepository

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
    decrypt(isError = false)
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
      val timeout = settings.getString(PreferenceKeys.GENERAL_SHOW_TIME)?.toIntOrNull() ?: 60
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

  private fun decrypt(isError: Boolean) {
    if (retries < MAX_RETRIES) {
      retries += 1
    } else {
      finish()
    }
    val dialog = PasswordDialog()
    if (isError) {
      dialog.setError()
    }
    lifecycleScope.launch(Dispatchers.Main) {
      dialog.password.collectLatest { value ->
        if (value != null) {
          val res = runCatching { decrypt(value) }
          if (res.isErr()) {
            logcat(ERROR) { res.unwrapError().stackTraceToString() }
            decrypt(isError = true)
          }
        }
      }
    }
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
  }

  private suspend fun decrypt(password: String) {
    val message = withContext(Dispatchers.IO) { File(fullPath).readBytes().inputStream() }
    val result =
      withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        repository.decrypt(
          password,
          message,
          outputStream,
        )
        outputStream
      }
    startAutoDismissTimer()

    val entry = passwordEntryFactory.create(result.toByteArray())
    passwordEntry = entry
    createPasswordUi(entry)
  }

  private suspend fun createPasswordUi(entry: PasswordEntry) =
    withContext(Dispatchers.Main) {
      val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
      invalidateOptionsMenu()

      val items = arrayListOf<FieldItem>()
      if (!entry.password.isNullOrBlank()) {
        items.add(FieldItem.createPasswordField(entry.password!!))
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
        entry.totp.onEach(adapter::updateOTPCode).launchIn(lifecycleScope)
      }
    }

  private companion object {
    private const val MAX_RETRIES = 3
  }
}

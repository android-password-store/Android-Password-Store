/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.crypto.CryptoRepository
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.DecryptLayoutBinding
import app.passwordstore.ui.adapters.FieldItemAdapter
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.Constants
import app.passwordstore.util.settings.PreferenceKeys
import app.passwordstore.util.viewmodel.PasswordViewModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
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

  private val viewModel: PasswordViewModel by viewModels()
  private val binding by viewBinding(DecryptLayoutBinding::inflate)
  private val relativeParentPath by unsafeLazy { getParentPath(fullPath, repoPath) }
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var repository: CryptoRepository
  @Inject lateinit var dispatcherProvider: DispatcherProvider

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
    askPassphrase()
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

  private fun askPassphrase() {
    if (retries < MAX_RETRIES) {
      retries += 1
    } else {
      finish()
    }
    showPasswordDialog(isError = false)
    lifecycleScope.launch(dispatcherProvider.main()) {
      viewModel.password.collectLatest { value ->
        when (val result = decryptWithPassphrase(value)) {
          is Ok -> {
            val entry = passwordEntryFactory.create(result.value.toByteArray())
            passwordEntry = entry
            createPasswordUI(entry)
            startAutoDismissTimer()
          }
          is Err -> {
            logcat(ERROR) { result.error.stackTraceToString() }
            showPasswordDialog(isError = true)
          }
        }
      }
    }
  }

  private fun showPasswordDialog(isError: Boolean) {
    val dialog = PasswordDialog()
    if (isError) {
      dialog.setError()
    }
    dialog.show(supportFragmentManager, "PASSWORD_DIALOG")
  }

  private suspend fun decryptWithPassphrase(password: String) = runCatching {
    val message = withContext(dispatcherProvider.io()) { File(fullPath).readBytes().inputStream() }
    val outputStream = ByteArrayOutputStream()
    val result =
      repository.decrypt(
        password,
        message,
        outputStream,
      )
    when (result) {
      is Ok -> outputStream
      is Err -> throw result.error
    }
  }

  private suspend fun createPasswordUI(entry: PasswordEntry) =
    withContext(dispatcherProvider.main()) {
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

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.sshkeygen

import android.content.SharedPreferences
import android.os.Bundle
import android.security.keystore.UserNotAuthenticatedException
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.databinding.ActivitySshKeygenBinding
import app.passwordstore.injection.prefs.GitPreferences
import app.passwordstore.ssh.SSHKeyAlgorithm
import app.passwordstore.ssh.SSHKeyManager
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result
import app.passwordstore.util.extensions.keyguardManager
import app.passwordstore.util.extensions.viewBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SshKeyGenActivity : AppCompatActivity() {

  private var sshKeyAlgorithm = SSHKeyAlgorithm.ECDSA
  private val binding by viewBinding(ActivitySshKeygenBinding::inflate)
  @GitPreferences @Inject lateinit var gitPrefs: SharedPreferences
  @Inject lateinit var sshKeyManager: SSHKeyManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    with(binding) {
      generate.setOnClickListener {
        lifecycleScope.launch {
          if (sshKeyManager.keyExists()) {
            MaterialAlertDialogBuilder(this@SshKeyGenActivity).run {
              setTitle(R.string.ssh_keygen_existing_title)
              setMessage(R.string.ssh_keygen_existing_message)
              setPositiveButton(R.string.ssh_keygen_existing_replace) { _, _ ->
                lifecycleScope.launch { generate() }
              }
              setNegativeButton(R.string.ssh_keygen_existing_keep) { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
              }
              show()
            }
          } else {
            generate()
          }
        }
      }
      keyTypeGroup.check(R.id.key_type_ecdsa)
      keyTypeExplanation.setText(R.string.ssh_keygen_explanation_ecdsa)
      keyTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked) {
          sshKeyAlgorithm =
            when (checkedId) {
              R.id.key_type_ed25519 -> SSHKeyAlgorithm.ED25519
              R.id.key_type_ecdsa -> SSHKeyAlgorithm.ECDSA
              R.id.key_type_rsa -> SSHKeyAlgorithm.RSA
              else -> throw IllegalStateException("Impossible key type selection")
            }
          keyTypeExplanation.setText(
            when (sshKeyAlgorithm) {
              SSHKeyAlgorithm.ED25519 -> R.string.ssh_keygen_explanation_ed25519
              SSHKeyAlgorithm.ECDSA -> R.string.ssh_keygen_explanation_ecdsa
              SSHKeyAlgorithm.RSA -> R.string.ssh_keygen_explanation_rsa
            }
          )
        }
      }
      keyRequireAuthentication.isEnabled = keyguardManager.isDeviceSecure
      keyRequireAuthentication.isChecked = keyRequireAuthentication.isEnabled
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        onBackPressedDispatcher.onBackPressed()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private suspend fun generate() {
    binding.generate.apply {
      text = getString(R.string.ssh_key_gen_generating_progress)
      isEnabled = false
    }
    binding.generate.text = getString(R.string.ssh_key_gen_generating_progress)
    val result = runCatching {
      withContext(Dispatchers.IO) {
        val requireAuthentication = binding.keyRequireAuthentication.isChecked
        if (requireAuthentication) {
          val result =
            withContext(Dispatchers.Main) {
              suspendCoroutine<Result> { cont ->
                BiometricAuthenticator.authenticate(
                  this@SshKeyGenActivity,
                  R.string.biometric_prompt_title_ssh_keygen
                ) { result ->
                  // Do not cancel on failed attempts as these are handled by the
                  // authenticator UI.
                  if (result !is Result.Retry) cont.resume(result)
                }
              }
            }
          if (result !is Result.Success)
            throw UserNotAuthenticatedException(getString(R.string.biometric_auth_generic_failure))
        }
        sshKeyManager.generateKey(sshKeyAlgorithm, requireAuthentication)
      }
    }
    // Check if we still need this
    gitPrefs.edit { remove("ssh_key_local_passphrase") }
    binding.generate.apply {
      text = getString(R.string.ssh_keygen_generate)
      isEnabled = true
    }
    result.fold(
      success = { ShowSshKeyFragment().show(supportFragmentManager, "public_key") },
      failure = { e ->
        e.printStackTrace()
        MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.error_generate_ssh_key))
          .setMessage(getString(R.string.ssh_key_error_dialog_text) + e.message)
          .setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
            setResult(RESULT_OK)
            finish()
          }
          .show()
      },
    )
    hideKeyboard()
  }

  private fun hideKeyboard() {
    val imm = getSystemService<InputMethodManager>() ?: return
    var view = currentFocus
    if (view == null) {
      view = View(this)
    }
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }
}

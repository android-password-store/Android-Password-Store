/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.sshkeygen

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
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.ActivitySshKeygenBinding
import dev.msfjarvis.aps.injection.prefs.GitPreferences
import dev.msfjarvis.aps.util.auth.BiometricAuthenticator
import dev.msfjarvis.aps.util.auth.BiometricAuthenticator.Result
import dev.msfjarvis.aps.util.extensions.keyguardManager
import dev.msfjarvis.aps.util.extensions.viewBinding
import dev.msfjarvis.aps.util.git.sshj.SshKey
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class KeyGenType(val generateKey: suspend (requireAuthentication: Boolean) -> Unit) {
  Rsa({ requireAuthentication ->
    SshKey.generateKeystoreNativeKey(SshKey.Algorithm.Rsa, requireAuthentication)
  }),
  Ecdsa({ requireAuthentication ->
    SshKey.generateKeystoreNativeKey(SshKey.Algorithm.Ecdsa, requireAuthentication)
  }),
  Ed25519({ requireAuthentication ->
    SshKey.generateKeystoreWrappedEd25519Key(requireAuthentication)
  }),
}

@AndroidEntryPoint
class SshKeyGenActivity : AppCompatActivity() {

  private var keyGenType = KeyGenType.Ecdsa
  private val binding by viewBinding(ActivitySshKeygenBinding::inflate)
  @GitPreferences @Inject lateinit var gitPrefs: SharedPreferences

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    with(binding) {
      generate.setOnClickListener {
        if (SshKey.exists) {
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
          lifecycleScope.launch { generate() }
        }
      }
      keyTypeGroup.check(R.id.key_type_ecdsa)
      keyTypeExplanation.setText(R.string.ssh_keygen_explanation_ecdsa)
      keyTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked) {
          keyGenType =
            when (checkedId) {
              R.id.key_type_ed25519 -> KeyGenType.Ed25519
              R.id.key_type_ecdsa -> KeyGenType.Ecdsa
              R.id.key_type_rsa -> KeyGenType.Rsa
              else -> throw IllegalStateException("Impossible key type selection")
            }
          keyTypeExplanation.setText(
            when (keyGenType) {
              KeyGenType.Ed25519 -> R.string.ssh_keygen_explanation_ed25519
              KeyGenType.Ecdsa -> R.string.ssh_keygen_explanation_ecdsa
              KeyGenType.Rsa -> R.string.ssh_keygen_explanation_rsa
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
        onBackPressed()
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
                  // Do not cancel on failed attempts as these are handled by the authenticator UI.
                  if (result !is Result.Retry) cont.resume(result)
                }
              }
            }
          if (result !is Result.Success)
            throw UserNotAuthenticatedException(getString(R.string.biometric_auth_generic_failure))
        }
        keyGenType.generateKey(requireAuthentication)
      }
    }
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

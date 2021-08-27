/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.git.operation

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.injection.prefs.GitPreferences
import dev.msfjarvis.aps.util.git.sshj.InteractivePasswordFinder
import dev.msfjarvis.aps.util.settings.AuthMode
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class CredentialFinder(val callingActivity: FragmentActivity, val authMode: AuthMode) :
  InteractivePasswordFinder() {

  private val hiltEntryPoint =
    EntryPointAccessors.fromApplication(
      callingActivity.applicationContext,
      CredentialFinderEntryPoint::class.java,
    )

  override fun askForPassword(cont: Continuation<String?>, isRetry: Boolean) {
    val gitOperationPrefs = hiltEntryPoint.gitPrefs()
    val credentialPref: String
    @StringRes val messageRes: Int
    @StringRes val hintRes: Int
    @StringRes val rememberRes: Int
    @StringRes val errorRes: Int
    when (authMode) {
      AuthMode.SshKey -> {
        credentialPref = PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE
        messageRes = R.string.passphrase_dialog_text
        hintRes = R.string.ssh_keygen_passphrase
        rememberRes = R.string.git_operation_remember_passphrase
        errorRes = R.string.git_operation_wrong_passphrase
      }
      AuthMode.Password -> {
        // Could be either an SSH or an HTTPS password
        credentialPref = PreferenceKeys.HTTPS_PASSWORD
        messageRes = R.string.password_dialog_text
        hintRes = R.string.git_operation_hint_password
        rememberRes = R.string.git_operation_remember_password
        errorRes = R.string.git_operation_wrong_password
      }
      else ->
        throw IllegalStateException("Only SshKey and Password connection mode ask for passwords")
    }
    if (isRetry) gitOperationPrefs.edit { remove(credentialPref) }
    val storedCredential = gitOperationPrefs.getString(credentialPref, null)
    if (storedCredential == null) {
      val layoutInflater = LayoutInflater.from(callingActivity)

      @SuppressLint("InflateParams")
      val dialogView = layoutInflater.inflate(R.layout.git_credential_layout, null)
      val credentialLayout =
        dialogView.findViewById<TextInputLayout>(R.id.git_auth_passphrase_layout)
      val editCredential = dialogView.findViewById<TextInputEditText>(R.id.git_auth_credential)
      editCredential.setHint(hintRes)
      val rememberCredential =
        dialogView.findViewById<MaterialCheckBox>(R.id.git_auth_remember_credential)
      rememberCredential.setText(rememberRes)
      if (isRetry) {
        credentialLayout.error = callingActivity.resources.getString(errorRes)
        // Reset error when user starts entering a password
        editCredential.doOnTextChanged { _, _, _, _ -> credentialLayout.error = null }
      }
      MaterialAlertDialogBuilder(callingActivity)
        .run {
          setTitle(R.string.passphrase_dialog_title)
          setMessage(messageRes)
          setView(dialogView)
          setPositiveButton(R.string.dialog_ok) { _, _ ->
            val credential = editCredential.text.toString()
            if (rememberCredential.isChecked) {
              gitOperationPrefs.edit { putString(credentialPref, credential) }
            }
            cont.resume(credential)
          }
          setNegativeButton(R.string.dialog_cancel) { _, _ -> cont.resume(null) }
          setOnCancelListener { cont.resume(null) }
          create()
        }
        .run {
          window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
          show()
        }
    } else {
      cont.resume(storedCredential)
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface CredentialFinderEntryPoint {
    @GitPreferences fun gitPrefs(): SharedPreferences
  }
}

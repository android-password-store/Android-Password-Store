/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.crypto

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.DialogPasswordEntryBinding
import dev.msfjarvis.aps.util.extensions.finish
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [DialogFragment] to request a password from the user and forward it along. */
class PasswordDialog : DialogFragment() {

  private val binding by unsafeLazy { DialogPasswordEntryBinding.inflate(layoutInflater) }
  private var isError: Boolean = false
  private val _password = MutableStateFlow<String?>(null)
  val password = _password.asStateFlow()

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    builder.setView(binding.root)
    builder.setTitle(R.string.password)
    builder.setPositiveButton(android.R.string.ok) { _, _ -> tryEmitPassword() }
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.setOnShowListener {
      if (isError) {
        binding.passwordField.error = getString(R.string.git_operation_wrong_password)
      }
      binding.passwordEditText.doOnTextChanged { _, _, _, _ -> binding.passwordField.error = null }
      binding.passwordEditText.setOnKeyListener { _, keyCode, _ ->
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          tryEmitPassword()
          return@setOnKeyListener true
        }
        false
      }
    }
    return dialog
  }

  fun setError() {
    isError = true
  }

  override fun onCancel(dialog: DialogInterface) {
    super.onCancel(dialog)
    finish()
  }

  @Suppress("ControlFlowWithEmptyBody")
  private fun tryEmitPassword() {
    do {} while (!_password.tryEmit(binding.passwordEditText.text.toString()))
    dismissAllowingStateLoss()
  }
}

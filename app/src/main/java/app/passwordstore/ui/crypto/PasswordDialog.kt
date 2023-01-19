/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import app.passwordstore.R
import app.passwordstore.databinding.DialogPasswordEntryBinding
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.viewmodel.PasswordViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** [DialogFragment] to request a password from the user and forward it along. */
class PasswordDialog : DialogFragment() {

  private val viewModel: PasswordViewModel by activityViewModels()
  private val binding by unsafeLazy { DialogPasswordEntryBinding.inflate(layoutInflater) }
  private var isError: Boolean = false

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    builder.setView(binding.root)
    builder.setTitle(R.string.password)
    builder.setPositiveButton(android.R.string.ok) { _, _ -> setPasswordAndDismiss() }
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.setOnShowListener {
      if (isError) {
        binding.passwordField.error = getString(R.string.git_operation_wrong_password)
      }
      binding.passwordEditText.doOnTextChanged { _, _, _, _ -> binding.passwordField.error = null }
      binding.passwordEditText.setOnKeyListener { _, keyCode, _ ->
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          setPasswordAndDismiss()
          return@setOnKeyListener true
        }
        false
      }
    }
    dialog.window?.setFlags(
      WindowManager.LayoutParams.FLAG_SECURE,
      WindowManager.LayoutParams.FLAG_SECURE,
    )
    return dialog
  }

  fun setError() {
    isError = true
  }

  override fun onCancel(dialog: DialogInterface) {
    super.onCancel(dialog)
    finish()
  }

  private fun setPasswordAndDismiss() {
    viewModel.setPassword(binding.passwordEditText.text.toString())
    dismissAllowingStateLoss()
  }
}

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
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import app.passwordstore.R
import app.passwordstore.databinding.DialogPasswordEntryBinding
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.unsafeLazy
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** [DialogFragment] to request a password from the user and forward it along. */
class PasswordDialog(private val enabledCache: Boolean, private val clearCache: Boolean) :
  DialogFragment() {

  private val binding by unsafeLazy { DialogPasswordEntryBinding.inflate(layoutInflater) }
  private var isError: Boolean = false
  private var clearCacheChecked: Boolean = true

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    builder.setView(binding.root)
    builder.setTitle(R.string.password)

    if (enabledCache) {
      builder.setMultiChoiceItems(
        arrayOf(getString(R.string.clear_cached_password_on_screen_off)),
        BooleanArray(1) { clearCache },
      ) { _, _, isChecked ->
        if (isChecked) clearCacheChecked = true else clearCacheChecked = false
      }
    }

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
    val password = binding.passwordEditText.text.toString()
    setFragmentResult(
      PASSWORD_RESULT_KEY,
      bundleOf(PASSWORD_PHRASE_KEY to password, PASSWORD_CLEAR_KEY to clearCacheChecked),
    )
    dismissAllowingStateLoss()
  }

  companion object {
    const val PASSWORD_RESULT_KEY = "password_result"
    const val PASSWORD_PHRASE_KEY = "password_phrase"
    const val PASSWORD_CLEAR_KEY = "password_clear"
  }
}

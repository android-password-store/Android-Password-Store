/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import app.passwordstore.databinding.DialogTextInputBinding
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.unsafeLazy
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * General purpose [DialogFragment] that can be used to accept a single text input. It provides a
 * configurable title and text input hint through the instantiation helper at
 * [TextInputDialog.newInstance]. Typical usage would look something like this:
 * ```kotlin
 * val dialog = TextInputDialog.newInstance(getString(R.string.dialog_title), getString(R.string.dialog_hint))
 * dialog.show(supportFragmentManager, "text_input_dialog")
 * dialog.setFragmentResultListener(TextInputDialog.REQUEST_KEY) { _, bundle ->
 *   doSomething(bundle.getString(TextInputDialog.BUNDLE_KEY_TEXT))
 * }
 * ```
 */
class TextInputDialog : DialogFragment() {
  private val binding by unsafeLazy { DialogTextInputBinding.inflate(layoutInflater) }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    builder.setView(binding.root)
    builder.setTitle(arguments?.getString(BUNDLE_KEY_TITLE))
    arguments?.getString(BUNDLE_KEY_HINT)?.let { hint ->
      binding.textInputLayout.isHintEnabled = true
      binding.textInputLayout.isHintAnimationEnabled = true
      binding.textInputLayout.hint = hint
    }
    builder.setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
      setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_TEXT to binding.editText.text.toString()))
      dialogInterface.dismiss()
    }
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    return dialog
  }

  override fun onCancel(dialog: DialogInterface) {
    super.onCancel(dialog)
    finish()
  }

  companion object {
    const val REQUEST_KEY = "text_input_dialog"
    const val BUNDLE_KEY_TEXT = "text"
    private const val BUNDLE_KEY_TITLE = "title"
    private const val BUNDLE_KEY_HINT = "hint"

    fun newInstance(title: String, hint: String? = null): TextInputDialog {
      val args = bundleOf(BUNDLE_KEY_TITLE to title, BUNDLE_KEY_HINT to hint)
      val dialog = TextInputDialog()
      dialog.arguments = args
      return dialog
    }
  }
}

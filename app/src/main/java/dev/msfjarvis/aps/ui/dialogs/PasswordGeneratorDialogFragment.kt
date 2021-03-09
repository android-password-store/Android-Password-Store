/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.FragmentPwgenBinding
import dev.msfjarvis.aps.ui.crypto.PasswordCreationActivity
import dev.msfjarvis.aps.util.pwgen.PasswordGenerator
import dev.msfjarvis.aps.util.pwgen.PasswordGenerator.generate
import dev.msfjarvis.aps.util.pwgen.PasswordGenerator.setPrefs
import dev.msfjarvis.aps.util.pwgen.PasswordOption
import dev.msfjarvis.aps.util.settings.PreferenceKeys

class PasswordGeneratorDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    val callingActivity = requireActivity()
    val binding = FragmentPwgenBinding.inflate(layoutInflater)
    val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")
    val prefs = requireActivity().applicationContext.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

    builder.setView(binding.root)

    binding.numerals.isChecked = !prefs.getBoolean(PasswordOption.NoDigits.key, false)
    binding.symbols.isChecked = prefs.getBoolean(PasswordOption.AtLeastOneSymbol.key, false)
    binding.uppercase.isChecked = !prefs.getBoolean(PasswordOption.NoUppercaseLetters.key, false)
    binding.lowercase.isChecked = !prefs.getBoolean(PasswordOption.NoLowercaseLetters.key, false)
    binding.ambiguous.isChecked = !prefs.getBoolean(PasswordOption.NoAmbiguousCharacters.key, false)
    binding.pronounceable.isChecked = !prefs.getBoolean(PasswordOption.FullyRandom.key, true)

    binding.lengthNumber.setText(prefs.getInt(PreferenceKeys.LENGTH, 20).toString())
    binding.passwordText.typeface = monoTypeface
    return builder
      .run {
        setTitle(R.string.pwgen_title)
        setPositiveButton(R.string.dialog_ok) { _, _ ->
          setFragmentResult(
            PasswordCreationActivity.PASSWORD_RESULT_REQUEST_KEY,
            bundleOf(PasswordCreationActivity.RESULT to "${binding.passwordText.text}")
          )
        }
        setNeutralButton(R.string.dialog_cancel) { _, _ -> }
        setNegativeButton(R.string.pwgen_generate, null)
        create()
      }
      .apply {
        setOnShowListener {
          generate(binding.passwordText)
          getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { generate(binding.passwordText) }
        }
      }
  }

  private fun generate(passwordField: AppCompatTextView) {
    setPreferences()
    passwordField.text =
      runCatching { generate(requireContext().applicationContext) }.getOrElse { e ->
        Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
        ""
      }
  }

  private fun isChecked(@IdRes id: Int): Boolean {
    return requireDialog().findViewById<CheckBox>(id).isChecked
  }

  private fun setPreferences() {
    val preferences =
      listOfNotNull(
        PasswordOption.NoDigits.takeIf { !isChecked(R.id.numerals) },
        PasswordOption.AtLeastOneSymbol.takeIf { isChecked(R.id.symbols) },
        PasswordOption.NoUppercaseLetters.takeIf { !isChecked(R.id.uppercase) },
        PasswordOption.NoAmbiguousCharacters.takeIf { !isChecked(R.id.ambiguous) },
        PasswordOption.FullyRandom.takeIf { !isChecked(R.id.pronounceable) },
        PasswordOption.NoLowercaseLetters.takeIf { !isChecked(R.id.lowercase) }
      )
    val lengthText = requireDialog().findViewById<EditText>(R.id.lengthNumber).text.toString()
    val length = lengthText.toIntOrNull()?.takeIf { it >= 0 } ?: PasswordGenerator.DEFAULT_LENGTH
    setPrefs(requireActivity().applicationContext, preferences, length)
  }
}

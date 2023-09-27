/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.dialogs

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
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.databinding.FragmentPwgenBinding
import app.passwordstore.passgen.random.MaxIterationsExceededException
import app.passwordstore.passgen.random.NoCharactersIncludedException
import app.passwordstore.passgen.random.PasswordGenerator
import app.passwordstore.passgen.random.PasswordLengthTooShortException
import app.passwordstore.passgen.random.PasswordOption
import app.passwordstore.ui.crypto.PasswordCreationActivity
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.android.widget.afterTextChanges
import reactivecircus.flowbinding.android.widget.checkedChanges

class PasswordGeneratorDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val prefs = requireContext().getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)
    val builder = MaterialAlertDialogBuilder(requireContext())

    val binding = FragmentPwgenBinding.inflate(layoutInflater)
    builder.setView(binding.root)

    binding.numerals.isChecked = !prefs.getBoolean(PasswordOption.NoDigits.key, false)
    binding.symbols.isChecked = prefs.getBoolean(PasswordOption.AtLeastOneSymbol.key, false)
    binding.uppercase.isChecked = !prefs.getBoolean(PasswordOption.NoUppercaseLetters.key, false)
    binding.lowercase.isChecked = !prefs.getBoolean(PasswordOption.NoLowercaseLetters.key, false)
    binding.ambiguous.isChecked = !prefs.getBoolean(PasswordOption.NoAmbiguousCharacters.key, false)
    binding.pronounceable.isChecked = !prefs.getBoolean(PasswordOption.FullyRandom.key, true)
    binding.lengthNumber.setText(prefs.getInt(PreferenceKeys.LENGTH, 20).toString())
    binding.passwordText.typeface = Typeface.MONOSPACE

    lifecycleScope.launch {
      merge(
          binding.numerals.checkedChanges().skipInitialValue(),
          binding.symbols.checkedChanges().skipInitialValue(),
          binding.uppercase.checkedChanges().skipInitialValue(),
          binding.lowercase.checkedChanges().skipInitialValue(),
          binding.ambiguous.checkedChanges().skipInitialValue(),
          binding.pronounceable.checkedChanges().skipInitialValue(),
          binding.lengthNumber.afterTextChanges().skipInitialValue(),
        )
        .collect { generate(binding.passwordText) }
    }

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
          getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            generate(binding.passwordText)
          }
        }
      }
  }

  private fun generate(passwordField: AppCompatTextView) {
    val passwordOptions = getSelectedOptions()
    val passwordLength = getLength()
    setPrefs(requireContext(), passwordOptions, passwordLength)
    passwordField.text =
      runCatching { PasswordGenerator.generate(passwordOptions, passwordLength) }
        .getOrElse { exception ->
          val errorText =
            when (exception) {
              is MaxIterationsExceededException ->
                requireContext().getString(R.string.pwgen_max_iterations_exceeded)
              is NoCharactersIncludedException ->
                requireContext().getString(R.string.pwgen_no_chars_error)
              is PasswordLengthTooShortException ->
                requireContext().getString(R.string.pwgen_length_too_short_error)
              else -> requireContext().getString(R.string.pwgen_some_error_occurred)
            }
          Toast.makeText(requireActivity(), errorText, Toast.LENGTH_SHORT).show()
          ""
        }
  }

  private fun isChecked(@IdRes id: Int): Boolean {
    return requireDialog().findViewById<CheckBox>(id).isChecked
  }

  private fun getSelectedOptions(): List<PasswordOption> {
    return listOfNotNull(
      PasswordOption.NoDigits.takeIf { !isChecked(R.id.numerals) },
      PasswordOption.AtLeastOneSymbol.takeIf { isChecked(R.id.symbols) },
      PasswordOption.NoUppercaseLetters.takeIf { !isChecked(R.id.uppercase) },
      PasswordOption.NoAmbiguousCharacters.takeIf { !isChecked(R.id.ambiguous) },
      PasswordOption.FullyRandom.takeIf { !isChecked(R.id.pronounceable) },
      PasswordOption.NoLowercaseLetters.takeIf { !isChecked(R.id.lowercase) }
    )
  }

  private fun getLength(): Int {
    val lengthText = requireDialog().findViewById<EditText>(R.id.lengthNumber).text.toString()
    return lengthText.toIntOrNull()?.takeIf { it >= 0 } ?: PasswordGenerator.DEFAULT_LENGTH
  }

  /**
   * Enables the [PasswordOption]s in [options] and sets [targetLength] as the length for generated
   * passwords.
   */
  private fun setPrefs(ctx: Context, options: List<PasswordOption>, targetLength: Int): Boolean {
    ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE).edit {
      for (possibleOption in PasswordOption.entries) {
        putBoolean(possibleOption.key, possibleOption in options)
      }
      putInt("length", targetLength)
    }
    return true
  }
}

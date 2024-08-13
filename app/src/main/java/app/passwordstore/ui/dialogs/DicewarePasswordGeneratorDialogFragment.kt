/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.databinding.FragmentPwgenDicewareBinding
import app.passwordstore.injection.prefs.PasswordGeneratorPreferences
import app.passwordstore.passgen.diceware.DicewarePassphraseGenerator
import app.passwordstore.ui.crypto.PasswordCreationActivity
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.settings.PreferenceKeys.DICEWARE_LENGTH
import app.passwordstore.util.settings.PreferenceKeys.DICEWARE_SEPARATOR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.android.widget.afterTextChanges

@AndroidEntryPoint
class DicewarePasswordGeneratorDialogFragment : DialogFragment() {

  @Inject lateinit var dicewareGenerator: DicewarePassphraseGenerator
  @Inject @PasswordGeneratorPreferences lateinit var prefs: SharedPreferences

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())

    val binding = FragmentPwgenDicewareBinding.inflate(layoutInflater)
    builder.setView(binding.root)

    binding.passwordSeparatorText.setText(prefs.getString(DICEWARE_SEPARATOR) ?: "-")
    binding.passwordLengthText.setText(prefs.getInt(DICEWARE_LENGTH, 5).toString())
    binding.passwordText.typeface = Typeface.MONOSPACE

    lifecycleScope.launch {
      merge(
          binding.passwordLengthText.afterTextChanges(),
          binding.passwordSeparatorText.afterTextChanges(),
        )
        .collect { _ -> generatePassword(binding) }
    }
    return builder
      .run {
        setTitle(R.string.pwgen_title)
        setPositiveButton(R.string.dialog_ok) { _, _ ->
          setFragmentResult(
            PasswordCreationActivity.PASSWORD_RESULT_REQUEST_KEY,
            bundleOf(PasswordCreationActivity.RESULT to "${binding.passwordText.text}"),
          )
        }
        setNeutralButton(R.string.dialog_cancel) { _, _ -> }
        setNegativeButton(R.string.pwgen_generate, null)
        create()
      }
      .apply {
        setOnShowListener {
          generatePassword(binding)
          getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { generatePassword(binding) }
        }
      }
  }

  private fun generatePassword(binding: FragmentPwgenDicewareBinding) {
    val length = binding.passwordLengthText.text?.toString()?.toIntOrNull() ?: 5
    val separator = binding.passwordSeparatorText.text?.toString()?.getOrNull(0) ?: '-'
    setPreferences(length, separator)
    binding.passwordText.text = dicewareGenerator.generatePassphrase(length, separator)
  }

  private fun setPreferences(length: Int, separator: Char) {
    prefs.edit {
      putInt(DICEWARE_LENGTH, length)
      putString(DICEWARE_SEPARATOR, separator.toString())
    }
  }
}

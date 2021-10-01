/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.FragmentXkpwgenBinding
import dev.msfjarvis.aps.ui.crypto.PasswordCreationActivity
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.pwgenxkpwd.CapsType
import dev.msfjarvis.aps.util.pwgenxkpwd.PasswordBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import reactivecircus.flowbinding.android.widget.afterTextChanges
import reactivecircus.flowbinding.android.widget.selectionEvents

@OptIn(ExperimentalCoroutinesApi::class)
class XkPasswordGeneratorDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    val callingActivity = requireActivity()
    val inflater = callingActivity.layoutInflater
    val binding = FragmentXkpwgenBinding.inflate(inflater)
    val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")
    val prefs = callingActivity.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

    builder.setView(binding.root)

    val previousStoredCapStyle: String =
      runCatching { prefs.getString(PREF_KEY_CAPITALS_STYLE)!! }.getOr(DEFAULT_CAPS_STYLE)

    val lastCapitalsStyleIndex: Int =
      runCatching { CapsType.valueOf(previousStoredCapStyle).ordinal }.getOr(DEFAULT_CAPS_INDEX)
    binding.xkCapType.setSelection(lastCapitalsStyleIndex)
    binding.xkNumWords.setText(prefs.getString(PREF_KEY_NUM_WORDS, DEFAULT_NUMBER_OF_WORDS))

    binding.xkSeparator.setText(prefs.getString(PREF_KEY_SEPARATOR, DEFAULT_WORD_SEPARATOR))
    binding.xkNumberSymbolMask.setText(
      prefs.getString(PREF_KEY_EXTRA_SYMBOLS_MASK, DEFAULT_EXTRA_SYMBOLS_MASK)
    )

    binding.xkPasswordText.typeface = monoTypeface

    builder.setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
      setPreferences(binding, prefs)
      setFragmentResult(
        PasswordCreationActivity.PASSWORD_RESULT_REQUEST_KEY,
        bundleOf(PasswordCreationActivity.RESULT to "${binding.xkPasswordText.text}")
      )
    }

    // flip neutral and negative buttons
    builder.setNeutralButton(resources.getString(R.string.dialog_cancel)) { _, _ -> }
    builder.setNegativeButton(resources.getString(R.string.pwgen_generate), null)

    val dialog = builder.setTitle(this.resources.getString(R.string.xkpwgen_title)).create()

    // make parameter changes reactive and automatically update passwords
    merge(
        binding.xkSeparator.afterTextChanges().skipInitialValue(),
        binding.xkCapType.selectionEvents().skipInitialValue(),
        binding.xkNumWords.afterTextChanges().skipInitialValue(),
        binding.xkNumberSymbolMask.afterTextChanges().skipInitialValue(),
      )
      .onEach { updatePassword(binding, prefs) }
      .launchIn(lifecycleScope)

    dialog.setOnShowListener {
      updatePassword(binding, prefs)

      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
        updatePassword(binding, prefs)
      }
    }
    return dialog
  }

  private fun updatePassword(binding: FragmentXkpwgenBinding, prefs: SharedPreferences) {
    setPreferences(binding, prefs)
    makeAndSetPassword(binding)
  }

  private fun makeAndSetPassword(binding: FragmentXkpwgenBinding) {
    PasswordBuilder(requireContext())
      .setNumberOfWords(binding.xkNumWords.text.toString().ifBlank { "0" }.toInt())
      .setMinimumWordLength(DEFAULT_MIN_WORD_LENGTH)
      .setMaximumWordLength(DEFAULT_MAX_WORD_LENGTH)
      .setSeparator(binding.xkSeparator.text.toString())
      .appendNumbers(
        binding.xkNumberSymbolMask.text!!.count { c -> c == EXTRA_CHAR_PLACEHOLDER_DIGIT }
      )
      .appendSymbols(
        binding.xkNumberSymbolMask.text!!.count { c -> c == EXTRA_CHAR_PLACEHOLDER_SYMBOL }
      )
      .setCapitalization(CapsType.valueOf(binding.xkCapType.selectedItem.toString()))
      .create()
      .fold(
        success = { binding.xkPasswordText.text = it },
        failure = { e ->
          Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
          logcat("xkpw", ERROR) { "failure generating xkpasswd\n${e.asLog()}" }
          binding.xkPasswordText.text = FALLBACK_ERROR_PASS
        },
      )
  }

  private fun setPreferences(binding: FragmentXkpwgenBinding, prefs: SharedPreferences) {
    prefs.edit {
      putString(PREF_KEY_CAPITALS_STYLE, binding.xkCapType.selectedItem.toString())
      putString(PREF_KEY_NUM_WORDS, binding.xkNumWords.text.toString())
      putString(PREF_KEY_SEPARATOR, binding.xkSeparator.text.toString())
      putString(PREF_KEY_EXTRA_SYMBOLS_MASK, binding.xkNumberSymbolMask.text.toString())
    }
  }

  companion object {

    const val PREF_KEY_CAPITALS_STYLE = "pref_key_capitals_style"
    const val PREF_KEY_NUM_WORDS = "pref_key_num_words"
    const val PREF_KEY_SEPARATOR = "pref_key_separator"
    const val PREF_KEY_EXTRA_SYMBOLS_MASK = "pref_key_xkpwgen_extra_symbols_mask"
    val DEFAULT_CAPS_STYLE = CapsType.Sentence.name
    val DEFAULT_CAPS_INDEX = CapsType.Sentence.ordinal
    const val DEFAULT_NUMBER_OF_WORDS = "3"
    const val DEFAULT_WORD_SEPARATOR = "."
    const val DEFAULT_EXTRA_SYMBOLS_MASK = "ds"
    const val DEFAULT_MIN_WORD_LENGTH = 3
    const val DEFAULT_MAX_WORD_LENGTH = 9
    const val FALLBACK_ERROR_PASS = "42"
    const val EXTRA_CHAR_PLACEHOLDER_DIGIT = 'd'
    const val EXTRA_CHAR_PLACEHOLDER_SYMBOL = 's'
  }
}

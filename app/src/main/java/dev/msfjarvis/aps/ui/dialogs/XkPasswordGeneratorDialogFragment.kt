/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.github.ajalt.timberkt.Timber.tag
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.FragmentXkpwgenBinding
import dev.msfjarvis.aps.util.pwgenxkpwd.CapsType
import dev.msfjarvis.aps.util.pwgenxkpwd.PasswordBuilder
import dev.msfjarvis.aps.util.extensions.getString

/** A placeholder fragment containing a simple view.  */
class XkPasswordGeneratorDialogFragment : DialogFragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var binding: FragmentXkpwgenBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val callingActivity = requireActivity()
        val inflater = callingActivity.layoutInflater
        binding = FragmentXkpwgenBinding.inflate(inflater)

        val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")

        builder.setView(binding.root)

        prefs = callingActivity.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

        val previousStoredCapStyle: String = runCatching {
            prefs.getString(PREF_KEY_CAPITALS_STYLE)!!
        }.getOr(DEFAULT_CAPS_STYLE)

        val lastCapitalsStyleIndex: Int = runCatching {
            CapsType.valueOf(previousStoredCapStyle).ordinal
        }.getOr(DEFAULT_CAPS_INDEX)
        binding.xkCapType.setSelection(lastCapitalsStyleIndex)
        binding.xkNumWords.setText(prefs.getString(PREF_KEY_NUM_WORDS, DEFAULT_NUMBER_OF_WORDS))

        binding.xkSeparator.setText(prefs.getString(PREF_KEY_SEPARATOR, DEFAULT_WORD_SEPARATOR))
        binding.xkNumberSymbolMask.setText(prefs.getString(PREF_KEY_EXTRA_SYMBOLS_MASK, DEFAULT_EXTRA_SYMBOLS_MASK))

        binding.xkPasswordText.typeface = monoTypeface

        builder.setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
            setPreferences()
            val edit = callingActivity.findViewById<EditText>(R.id.password)
            edit.setText(binding.xkPasswordText.text)
        }

        // flip neutral and negative buttons
        builder.setNeutralButton(resources.getString(R.string.dialog_cancel)) { _, _ -> }
        builder.setNegativeButton(resources.getString(R.string.pwgen_generate), null)

        val dialog = builder.setTitle(this.resources.getString(R.string.xkpwgen_title)).create()

        dialog.setOnShowListener {
            setPreferences()
            makeAndSetPassword(binding.xkPasswordText)

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                setPreferences()
                makeAndSetPassword(binding.xkPasswordText)
            }
        }
        return dialog
    }

    private fun makeAndSetPassword(passwordText: AppCompatTextView) {
        PasswordBuilder(requireContext())
            .setNumberOfWords(Integer.valueOf(binding.xkNumWords.text.toString()))
            .setMinimumWordLength(DEFAULT_MIN_WORD_LENGTH)
            .setMaximumWordLength(DEFAULT_MAX_WORD_LENGTH)
            .setSeparator(binding.xkSeparator.text.toString())
            .appendNumbers(binding.xkNumberSymbolMask.text!!.count { c -> c == EXTRA_CHAR_PLACEHOLDER_DIGIT })
            .appendSymbols(binding.xkNumberSymbolMask.text!!.count { c -> c == EXTRA_CHAR_PLACEHOLDER_SYMBOL })
            .setCapitalization(CapsType.valueOf(binding.xkCapType.selectedItem.toString())).create()
            .fold(
                success = { passwordText.text = it },
                failure = { e ->
                    Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
                    tag("xkpw").e(e, "failure generating xkpasswd")
                    passwordText.text = FALLBACK_ERROR_PASS
                },
            )
    }

    private fun setPreferences() {
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

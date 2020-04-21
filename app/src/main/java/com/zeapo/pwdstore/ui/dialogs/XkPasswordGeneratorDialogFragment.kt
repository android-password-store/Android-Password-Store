/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.pwgen.PasswordGenerator
import com.zeapo.pwdstore.pwgenxkpwd.CapsType
import com.zeapo.pwdstore.pwgenxkpwd.PasswordBuilder
import timber.log.Timber

/** A placeholder fragment containing a simple view.  */
class XkPasswordGeneratorDialogFragment : DialogFragment() {

    private lateinit var editSeparator: AppCompatEditText
    private lateinit var editNumWords: AppCompatEditText
    private lateinit var cbSymbols: CheckBox
    private lateinit var spinnerCapsType: Spinner
    private lateinit var cbNumbers: CheckBox
    private lateinit var prefs: SharedPreferences
    private lateinit var spinnerNumbersCount: Spinner
    private lateinit var spinnerSymbolsCount: Spinner

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val callingActivity = requireActivity()
        val inflater = callingActivity.layoutInflater
        val view = inflater.inflate(R.layout.fragment_xkpwgen, null)

        val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")

        builder.setView(view)

        prefs = callingActivity.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

        cbNumbers = view.findViewById(R.id.xknumerals)
        cbNumbers.isChecked = prefs.getBoolean(PREF_KEY_USE_NUMERALS, false)

        spinnerNumbersCount = view.findViewById(R.id.xk_numbers_count)

        val storedNumbersCount = prefs.getInt(PREF_KEY_NUMBERS_COUNT, 0)
        spinnerNumbersCount.setSelection(storedNumbersCount)

        cbSymbols = view.findViewById(R.id.xksymbols)
        cbSymbols.isChecked = prefs.getBoolean(PREF_KEY_USE_SYMBOLS, false) != false
        spinnerSymbolsCount = view.findViewById(R.id.xk_symbols_count)
        val symbolsCount = prefs.getInt(PREF_KEY_SYMBOLS_COUNT, 0)
        spinnerSymbolsCount.setSelection(symbolsCount)

        val previousStoredCapStyle: String = try {
            prefs.getString(PREF_KEY_CAPITALS_STYLE, DEFAULT_CAPS_STYLE)!!
        } catch (e: Exception) {
            Timber.tag("xkpw").e(e)
            DEFAULT_CAPS_STYLE
        }
        spinnerCapsType = view.findViewById(R.id.xkCapType)

        val lastCapitalsStyleIndex: Int

        lastCapitalsStyleIndex = try {
            CapsType.valueOf(previousStoredCapStyle).ordinal
        } catch (e: Exception) {
            Timber.tag("xkpw").e(e)
            DEFAULT_CAPS_INDEX
        }
        spinnerCapsType.setSelection(lastCapitalsStyleIndex)

        editNumWords = view.findViewById(R.id.xk_num_words)
        editNumWords.setText(prefs.getString(PREF_KEY_NUM_WORDS, DEFAULT_NUMBER_OF_WORDS))

        editSeparator = view.findViewById(R.id.xk_separator)
        editSeparator.setText(prefs.getString(PREF_KEY_SEPARATOR, DEFAULT_WORD_SEPARATOR))

        val passwordText: AppCompatTextView = view.findViewById(R.id.xkPasswordText)
        passwordText.typeface = monoTypeface

        builder.setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
            setPreferences()
            val edit = callingActivity.findViewById<EditText>(R.id.crypto_password_edit)
            edit.setText(passwordText.text)
        }

        // flip neutral and negative buttons
        builder.setNeutralButton(resources.getString(R.string.dialog_cancel)) { _, _ -> }
        builder.setNegativeButton(resources.getString(R.string.pwgen_generate), null)

        val dialog = builder.setTitle(this.resources.getString(R.string.xkpwgen_title)).create()

        dialog.setOnShowListener {
            setPreferences()
            makeAndSetPassword(passwordText)

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                setPreferences()
                makeAndSetPassword(passwordText)
            }
        }
        return dialog
    }

    private fun makeAndSetPassword(passwordText: AppCompatTextView) {
        try {
            passwordText.text = PasswordBuilder(requireContext())
                    .setNumberOfWords(Integer.valueOf(editNumWords.text.toString()))
                    .setMinimumWordLength(DEFAULT_MIN_WORD_LENGTH)
                    .setMaximumWordLength(DEFAULT_MAX_WORD_LENGTH)
                    .setSeparator(editSeparator.text.toString())
                    .appendNumbers(if (cbNumbers.isChecked) Integer.parseInt(spinnerNumbersCount.selectedItem as String) else 0)
                    .appendSymbols(if (cbSymbols.isChecked) Integer.parseInt(spinnerSymbolsCount.selectedItem as String) else 0)
                    .setCapitalization(CapsType.valueOf(spinnerCapsType.selectedItem.toString())).create()
        } catch (e: PasswordGenerator.PasswordGeneratorExeption) {
            Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
            Timber.tag("xkpw").e(e, "failure generating xkpasswd")
            passwordText.text = FALLBACK_ERROR_PASS
        }
    }

    private fun setPreferences() {
        prefs.edit {
            putBoolean(PREF_KEY_USE_NUMERALS, cbNumbers.isChecked)
            putBoolean(PREF_KEY_USE_SYMBOLS, cbSymbols.isChecked)
            putString(PREF_KEY_CAPITALS_STYLE, spinnerCapsType.selectedItem.toString())
            putString(PREF_KEY_NUM_WORDS, editNumWords.text.toString())
            putString(PREF_KEY_SEPARATOR, editSeparator.text.toString())
            putInt(PREF_KEY_NUMBERS_COUNT, Integer.parseInt(spinnerNumbersCount.selectedItem as String) - 1)
            putInt(PREF_KEY_SYMBOLS_COUNT, Integer.parseInt(spinnerSymbolsCount.selectedItem as String) - 1)
        }
    }

    companion object {
        const val PREF_KEY_USE_NUMERALS = "pref_key_use_numerals"
        const val PREF_KEY_USE_SYMBOLS = "pref_key_use_symbols"
        const val PREF_KEY_CAPITALS_STYLE = "pref_key_capitals_style"
        const val PREF_KEY_NUM_WORDS = "pref_key_num_words"
        const val PREF_KEY_SEPARATOR = "pref_key_separator"
        const val PREF_KEY_NUMBERS_COUNT = "pref_key_xkpwgen_numbers_count"
        const val PREF_KEY_SYMBOLS_COUNT = "pref_key_symbols_count"
        val DEFAULT_CAPS_STYLE = CapsType.Sentencecase.name
        val DEFAULT_CAPS_INDEX = CapsType.Sentencecase.ordinal
        const val DEFAULT_NUMBER_OF_WORDS = "3"
        const val DEFAULT_WORD_SEPARATOR = "."
        const val DEFAULT_MIN_WORD_LENGTH = 3
        const val DEFAULT_MAX_WORD_LENGTH = 9
        const val FALLBACK_ERROR_PASS = "42"
    }
}

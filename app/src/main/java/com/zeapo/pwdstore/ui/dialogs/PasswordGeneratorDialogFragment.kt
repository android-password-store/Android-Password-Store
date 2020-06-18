/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.pwgen.PasswordGenerator
import com.zeapo.pwdstore.pwgen.PasswordGenerator.PasswordGeneratorException
import com.zeapo.pwdstore.pwgen.PasswordGenerator.generate
import com.zeapo.pwdstore.pwgen.PasswordGenerator.setPrefs
import com.zeapo.pwdstore.pwgen.PasswordOption

class PasswordGeneratorDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val callingActivity = requireActivity()
        val inflater = callingActivity.layoutInflater

        @SuppressLint("InflateParams")
        val view = inflater.inflate(R.layout.fragment_pwgen, null)
        val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")
        val prefs = requireActivity().applicationContext
            .getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

        view.findViewById<CheckBox>(R.id.numerals)?.isChecked = !prefs.getBoolean(PasswordOption.NoDigits.key, false)
        view.findViewById<CheckBox>(R.id.symbols)?.isChecked = prefs.getBoolean(PasswordOption.AtLeastOneSymbol.key, false)
        view.findViewById<CheckBox>(R.id.uppercase)?.isChecked = !prefs.getBoolean(PasswordOption.NoUppercaseLetters.key, false)
        view.findViewById<CheckBox>(R.id.lowercase)?.isChecked = !prefs.getBoolean(PasswordOption.NoLowercaseLetters.key, false)
        view.findViewById<CheckBox>(R.id.ambiguous)?.isChecked = !prefs.getBoolean(PasswordOption.NoAmbiguousCharacters.key, false)
        view.findViewById<CheckBox>(R.id.pronounceable)?.isChecked = !prefs.getBoolean(PasswordOption.FullyRandom.key, true)

        val textView: AppCompatEditText = view.findViewById(R.id.lengthNumber)
        textView.setText(prefs.getInt("length", 20).toString())
        val passwordText: AppCompatTextView = view.findViewById(R.id.passwordText)
        passwordText.typeface = monoTypeface
        return MaterialAlertDialogBuilder(requireContext()).run {
            setTitle(R.string.pwgen_title)
            setView(view)
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                val edit = callingActivity.findViewById<EditText>(R.id.password)
                edit.setText(passwordText.text)
            }
            setNeutralButton(R.string.dialog_cancel) { _, _ -> }
            setNegativeButton(R.string.pwgen_generate, null)
            create()
        }.apply {
            setOnShowListener {
                generate(passwordText)
                getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    generate(passwordText)
                }
            }
        }
    }

    private fun generate(passwordField: AppCompatTextView) {
        setPreferences()
        try {
            passwordField.text = generate(requireContext().applicationContext)
        } catch (e: PasswordGeneratorException) {
            Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
            passwordField.text = ""
        }
    }

    private fun isChecked(@IdRes id: Int): Boolean {
        return requireDialog().findViewById<CheckBox>(id).isChecked
    }

    private fun setPreferences() {
        val preferences = listOfNotNull(
            PasswordOption.NoDigits.takeIf { !isChecked(R.id.numerals) },
            PasswordOption.AtLeastOneSymbol.takeIf { isChecked(R.id.symbols) },
            PasswordOption.NoUppercaseLetters.takeIf { !isChecked(R.id.uppercase) },
            PasswordOption.NoAmbiguousCharacters.takeIf { !isChecked(R.id.ambiguous) },
            PasswordOption.FullyRandom.takeIf { !isChecked(R.id.pronounceable) },
            PasswordOption.NoLowercaseLetters.takeIf { !isChecked(R.id.lowercase) }
        )
        val lengthText = requireDialog().findViewById<EditText>(R.id.lengthNumber).text.toString()
        val length = lengthText.toIntOrNull()?.takeIf { it >= 0 }
            ?: PasswordGenerator.DEFAULT_LENGTH
        setPrefs(requireActivity().applicationContext, preferences, length)
    }
}

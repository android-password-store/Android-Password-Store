/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.pwgen.PasswordGenerator.PasswordGeneratorExeption
import com.zeapo.pwdstore.pwgen.PasswordGenerator.generate
import com.zeapo.pwdstore.pwgen.PasswordGenerator.setPrefs

/** A placeholder fragment containing a simple view.  */
class PasswordGeneratorDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val callingActivity = requireActivity()
        val inflater = callingActivity.layoutInflater
        val view = inflater.inflate(R.layout.fragment_pwgen, null)
        val monoTypeface = Typeface.createFromAsset(callingActivity.assets, "fonts/sourcecodepro.ttf")
        builder.setView(view)
        val prefs = requireActivity().applicationContext
            .getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

        view.findViewById<CheckBox>(R.id.numerals)?.isChecked = !prefs.getBoolean("0", false)
        view.findViewById<CheckBox>(R.id.symbols)?.isChecked = prefs.getBoolean("y", false)
        view.findViewById<CheckBox>(R.id.uppercase)?.isChecked = !prefs.getBoolean("A", false)
        view.findViewById<CheckBox>(R.id.lowercase)?.isChecked = !prefs.getBoolean("L", false)
        view.findViewById<CheckBox>(R.id.ambiguous)?.isChecked = !prefs.getBoolean("B", false)
        view.findViewById<CheckBox>(R.id.pronounceable)?.isChecked = !prefs.getBoolean("s", true)

        val textView: AppCompatEditText = view.findViewById(R.id.lengthNumber)
        textView.setText(prefs.getInt("length", 20).toString())
        val passwordText: AppCompatTextView = view.findViewById(R.id.passwordText)
        passwordText.typeface = monoTypeface
        builder.setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
            val edit = callingActivity.findViewById<EditText>(R.id.crypto_password_edit)
            edit.setText(passwordText.text)
        }
        builder.setNeutralButton(resources.getString(R.string.dialog_cancel)) { _, _ -> }
        builder.setNegativeButton(resources.getString(R.string.pwgen_generate), null)
        val dialog = builder.setTitle(this.resources.getString(R.string.pwgen_title)).create()
        dialog.setOnShowListener {
            setPreferences()
            try {
                passwordText.text = generate(requireActivity().applicationContext)[0]
            } catch (e: PasswordGeneratorExeption) {
                Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
                passwordText.text = ""
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                setPreferences()
                try {
                    passwordText.text = generate(callingActivity.applicationContext)[0]
                } catch (e: PasswordGeneratorExeption) {
                    Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
                    passwordText.text = ""
                }
            }
        }
        return dialog
    }

    private fun setPreferences() {
        val preferences = ArrayList<String>()
        if (!(dialog!!.findViewById<CheckBox>(R.id.numerals)).isChecked) {
            preferences.add("0")
        }
        if ((dialog!!.findViewById<CheckBox>(R.id.symbols)).isChecked) {
            preferences.add("y")
        }
        if (!(dialog!!.findViewById<CheckBox>(R.id.uppercase)).isChecked) {
            preferences.add("A")
        }
        if (!(dialog!!.findViewById<CheckBox>(R.id.ambiguous)).isChecked) {
            preferences.add("B")
        }
        if (!(dialog!!.findViewById<CheckBox>(R.id.pronounceable)).isChecked) {
            preferences.add("s")
        }
        if (!(dialog!!.findViewById<CheckBox>(R.id.lowercase)).isChecked) {
            preferences.add("L")
        }
        val editText = dialog!!.findViewById<EditText>(R.id.lengthNumber)
        try {
            val length = Integer.valueOf(editText.text.toString())
            setPrefs(requireActivity().applicationContext, preferences, length)
        } catch (e: NumberFormatException) {
            setPrefs(requireActivity().applicationContext, preferences)
        }
    }
}

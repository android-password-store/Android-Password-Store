/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R

class SshKeyGenFragment : Fragment() {

    private lateinit var checkBox: CheckBox
    private lateinit var comment: EditText
    private lateinit var generate: Button
    private lateinit var passphrase: TextInputEditText
    private lateinit var spinner: Spinner
    private lateinit var activity: SshKeyGenActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ssh_keygen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as SshKeyGenActivity
        findViews(view)
        val lengths = arrayOf(2048, 4096)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, lengths)
        spinner.adapter = adapter
        generate.setOnClickListener { generate() }
        checkBox.setOnCheckedChangeListener { _, isChecked: Boolean ->
            val selection = passphrase.selectionEnd
            if (isChecked) {
                passphrase.inputType = (
                        InputType.TYPE_CLASS_TEXT
                                or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            } else {
                passphrase.inputType = (
                        InputType.TYPE_CLASS_TEXT
                                or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            }
            passphrase.setSelection(selection)
        }
    }

    private fun findViews(view: View) {
        checkBox = view.findViewById(R.id.show_passphrase)
        comment = view.findViewById(R.id.comment)
        generate = view.findViewById(R.id.generate)
        passphrase = view.findViewById(R.id.passphrase)
        spinner = view.findViewById(R.id.length)
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    fun generate() {
        val length = (spinner.selectedItem as Int).toString()
        val passphrase = passphrase.text.toString()
        val comment = comment.text.toString()
        KeyGenerateTask(activity).execute(length, passphrase, comment)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService<InputMethodManager>()
        var view = activity.currentFocus
        if (view == null) {
            view = View(activity)
        }
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

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
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.FragmentSshKeygenBinding

class SshKeyGenFragment : Fragment() {

    private var keyLength = "4096"
    private var _binding: FragmentSshKeygenBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSshKeygenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            generate.setOnClickListener { generate(passphrase.text.toString(), comment.text.toString()) }
            showPassphrase.setOnCheckedChangeListener { _, isChecked: Boolean ->
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
            keyLengthGroup.check(R.id.key_length_4096)
            keyLengthGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.key_length_2048 -> keyLength = "2048"
                        R.id.key_length_4096 -> keyLength = "4096"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    fun generate(passphrase: String, comment: String) {
        val length = keyLength
        KeyGenerateTask(requireActivity()).execute(length, passphrase, comment)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val activity = activity ?: return
        val imm = activity.getSystemService<InputMethodManager>() ?: return
        var view = activity.currentFocus
        if (view == null) {
            view = View(activity)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

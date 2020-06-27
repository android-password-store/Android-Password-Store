/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.FragmentSshKeygenBinding
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SshKeyGenFragment : Fragment(R.layout.fragment_ssh_keygen) {

    private var keyLength = 4096
    private val binding by viewBinding(FragmentSshKeygenBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            generate.setOnClickListener {
                lifecycleScope.launch { generate(passphrase.text.toString(), comment.text.toString()) }
            }
            keyLengthGroup.check(R.id.key_length_4096)
            keyLengthGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.key_length_2048 -> keyLength = 2048
                        R.id.key_length_4096 -> keyLength = 4096
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    private suspend fun generate(passphrase: String, comment: String) {
        binding.generate.text = getString(R.string.ssh_key_gen_generating_progress)
        val e = try {
            withContext(Dispatchers.IO) {
                val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, keyLength)
                var file = File(requireActivity().filesDir, ".ssh_key")
                var out = FileOutputStream(file, false)
                if (passphrase.isNotEmpty()) {
                    kp?.writePrivateKey(out, passphrase.toByteArray())
                } else {
                    kp?.writePrivateKey(out)
                }
                file = File(requireActivity().filesDir, ".ssh_key.pub")
                out = FileOutputStream(file, false)
                kp?.writePublicKey(out, comment)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e
        } finally {
            requireContext().getEncryptedPrefs("git_operation").edit {
                remove("ssh_key_local_passphrase")
            }
        }
        val activity = requireActivity()
        binding.generate.text = getString(R.string.ssh_keygen_generating_done)
        if (e == null) {
            val df = ShowSshKeyFragment()
            df.show(requireActivity().supportFragmentManager, "public_key")
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            prefs.edit { putBoolean(PreferenceKeys.USE_GENERATED_KEY, true) }
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.error_generate_ssh_key))
                .setMessage(activity.getString(R.string.ssh_key_error_dialog_text) + e.message)
                .setPositiveButton(activity.getString(R.string.dialog_ok)) { _, _ ->
                    requireActivity().finish()
                }
                .show()
        }
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

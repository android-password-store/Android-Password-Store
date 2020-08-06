/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivitySshKeygenBinding
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshKeyGenActivity : AppCompatActivity() {

    private var keyLength = 4096
    private val binding by viewBinding(ActivitySshKeygenBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // The back arrow in the action bar should act the same as the back button.
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun generate(passphrase: String, comment: String) {
        binding.generate.text = getString(R.string.ssh_key_gen_generating_progress)
        val e = try {
            withContext(Dispatchers.IO) {
                val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, keyLength)
                var file = File(filesDir, ".ssh_key")
                var out = FileOutputStream(file, false)
                if (passphrase.isNotEmpty()) {
                    kp?.writePrivateKey(out, passphrase.toByteArray())
                } else {
                    kp?.writePrivateKey(out)
                }
                file = File(filesDir, ".ssh_key.pub")
                out = FileOutputStream(file, false)
                kp?.writePublicKey(out, comment)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e
        } finally {
            getEncryptedPrefs("git_operation").edit {
                remove("ssh_key_local_passphrase")
            }
        }
        binding.generate.text = getString(R.string.ssh_keygen_generating_done)
        if (e == null) {
            val df = ShowSshKeyFragment()
            df.show(supportFragmentManager, "public_key")
            sharedPrefs.edit { putBoolean("use_generated_key", true) }
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.error_generate_ssh_key))
                .setMessage(getString(R.string.ssh_key_error_dialog_text) + e.message)
                .setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
                    finish()
                }
                .show()
        }
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService<InputMethodManager>() ?: return
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.Timber
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.databinding.PasswordCreationActivityBinding
import com.zeapo.pwdstore.ui.dialogs.PasswordGeneratorDialogFragment
import com.zeapo.pwdstore.ui.dialogs.XkPasswordGeneratorDialogFragment
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

class PasswordCreationActivity : BasePgpActivity(R.layout.password_creation_activity), OpenPgpServiceConnection.OnBound {

    private val binding by viewBinding(PasswordCreationActivityBinding::inflate)

    private val suggestedName by lazy { intent.getStringExtra("SUGGESTED_NAME") }
    private val suggestedPass by lazy { intent.getStringExtra("SUGGESTED_PASS") }
    private val suggestedExtra by lazy { intent.getStringExtra("SUGGESTED_EXTRA") }
    private val shouldGeneratePassword by lazy { intent.getBooleanExtra("GENERATE_PASSWORD", false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this)
        with(binding) {
            generatePassword.setOnClickListener { generatePassword() }

            cryptoPasswordCategory.apply {
                if (suggestedName != null || suggestedPass != null || shouldGeneratePassword) {
                    isEnabled = true
                } else {
                    setBackgroundColor(getColor(android.R.color.transparent))
                }
                val path = PgpActivity.getRelativePath(fullPath, repoPath)
                // Keep empty path field visible if it is editable.
                if (path.isEmpty() && !isEnabled)
                    visibility = View.GONE
                else
                    setText(path)
            }
            suggestedName?.let { passwordFileEdit.setText(it) }
            // Allow the user to quickly switch between storing the username as the filename or
            // in the encrypted extras. This only makes sense if the directory structure is
            // FileBased.
            if (suggestedName == null &&
                AutofillPreferences.directoryStructure(this@PasswordCreationActivity) ==
                DirectoryStructure.FileBased
            ) {
                encryptUsername.apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        if (isChecked) {
                            // User wants to enable username encryption, so we add it to the
                            // encrypted extras as the first line.
                            val username = passwordFileEdit.text.toString()
                            val extras = "username:$username\n${cryptoExtraEdit.text}"

                            passwordFileEdit.setText("")
                            cryptoExtraEdit.setText(extras)
                        } else {
                            // User wants to disable username encryption, so we extract the
                            // username from the encrypted extras and use it as the filename.
                            val entry = PasswordEntry("PASSWORD\n${cryptoExtraEdit.text}")
                            val username = entry.username

                            // username should not be null here by the logic in
                            // updateEncryptUsernameState, but it could still happen due to
                            // input lag.
                            if (username != null) {
                                passwordFileEdit.setText(username)
                                cryptoExtraEdit.setText(entry.extraContentWithoutUsername)
                            }
                        }
                        updateEncryptUsernameState()
                    }
                }
                listOf(passwordFileEdit, cryptoExtraEdit).forEach {
                    it.doOnTextChanged { _, _, _, _ -> updateEncryptUsernameState() }
                }
            }
            suggestedPass?.let {
                cryptoPasswordEdit.setText(it)
                cryptoPasswordEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            suggestedExtra?.let { cryptoExtraEdit.setText(it) }
            if (shouldGeneratePassword) {
                generatePassword()
                cryptoPasswordEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.pgp_handler_new_password, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home, R.id.crypto_cancel_add -> {
                finish()
            }
            R.id.crypto_confirm_add -> encrypt()
            R.id.crypto_confirm_add_and_copy -> encrypt(true)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun generatePassword() {
        when (settings.getString("pref_key_pwgen_type", KEY_PWGEN_TYPE_CLASSIC)) {
            KEY_PWGEN_TYPE_CLASSIC -> PasswordGeneratorDialogFragment()
                .show(supportFragmentManager, "generator")
            KEY_PWGEN_TYPE_XKPASSWD -> XkPasswordGeneratorDialogFragment()
                .show(supportFragmentManager, "xkpwgenerator")
        }
    }

    private fun updateEncryptUsernameState() = with(binding) {
        encryptUsername.apply {
            if (visibility != View.VISIBLE)
                return@with
            val hasUsernameInFileName = passwordFileEdit.text.toString().isNotBlank()
            // Use PasswordEntry to parse extras for username
            val entry = PasswordEntry("PLACEHOLDER\n${cryptoExtraEdit.text}")
            val hasUsernameInExtras = entry.hasUsername()
            isEnabled = hasUsernameInFileName xor hasUsernameInExtras
            isChecked = hasUsernameInExtras
        }
    }

    /**
     * Encrypts the password and the extra content
     */
    private fun encrypt(copy: Boolean = false) = with(binding) {
        val editName = passwordFileEdit.text.toString().trim()
        val editPass = cryptoPasswordEdit.text.toString()
        val editExtra = cryptoExtraEdit.text.toString()

        if (editName.isEmpty()) {
            showSnackbar(resources.getString(R.string.file_toast_text))
            return@with
        }

        if (editPass.isEmpty() && editExtra.isEmpty()) {
            showSnackbar(resources.getString(R.string.empty_toast_text))
            return@with
        }

        if (copy) {
            copyPasswordToClipBoard()
        }

        val data = Intent()
        data.action = OpenPgpApi.ACTION_ENCRYPT

        // EXTRA_KEY_IDS requires long[]
        val longKeys = keyIDs.map { it.toLong() }
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, longKeys.toLongArray())
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

        // TODO Check if we could use PasswordEntry to generate the file
        val content = "$editPass\n$editExtra"
        val inputStream = ByteArrayInputStream(content.toByteArray(Charset.forName("UTF-8")))
        val outputStream = ByteArrayOutputStream()

        val path = when {
            intent.getBooleanExtra("fromDecrypt", false) -> fullPath
            // If we allowed the user to edit the relative path, we have to consider it here instead
            // of fullPath.
            cryptoPasswordCategory.isEnabled -> {
                val editRelativePath = cryptoPasswordCategory.text.toString().trim()
                if (editRelativePath.isEmpty()) {
                    showSnackbar(resources.getString(R.string.path_toast_text))
                    return
                }
                val passwordDirectory = File("$repoPath/${editRelativePath.trim('/')}")
                if (!passwordDirectory.exists() && !passwordDirectory.mkdir()) {
                    showSnackbar("Failed to create directory ${editRelativePath.trim('/')}")
                    return
                }

                "${passwordDirectory.path}/$editName.gpg"
            }
            else -> "$fullPath/$editName.gpg"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            api?.executeApiAsync(data, inputStream, outputStream) { result ->
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            // TODO This might fail, we should check that the write is successful
                            val file = File(path)
                            file.outputStream().use {
                                it.write(outputStream.toByteArray())
                            }

                            val returnIntent = Intent()
                            returnIntent.putExtra("CREATED_FILE", path)
                            returnIntent.putExtra("NAME", editName)
                            returnIntent.putExtra("LONG_NAME", getLongName(fullPath, repoPath, editName))

                            // if coming from decrypt screen->edit button
                            if (intent.getBooleanExtra("fromDecrypt", false)) {
                                returnIntent.putExtra("OPERATION", "EDIT")
                                returnIntent.putExtra("needCommit", true)
                            }

                            if (shouldGeneratePassword) {
                                val directoryStructure =
                                    AutofillPreferences.directoryStructure(applicationContext)
                                val entry = PasswordEntry(content)
                                returnIntent.putExtra("PASSWORD", entry.password)
                                val username = PasswordEntry(content).username
                                    ?: directoryStructure.getUsernameFor(file)
                                returnIntent.putExtra("USERNAME", username)
                            }

                            setResult(RESULT_OK, returnIntent)
                            finish()
                        } catch (e: Exception) {
                            Timber.e(e) { "An Exception occurred" }
                        }
                    }
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }

    private fun copyPasswordToClipBoard() {
        /*
        val clipboard = clipboard ?: return
        var pass = passwordEntry?.password

        if (findViewById<TextView>(R.id.crypto_password_show) == null) {
            if (editPass == null) {
                return
            } else {
                pass = editPass
            }
        }

        val clip = ClipData.newPlainText("pgp_handler_result_pm", pass)
        clipboard.setPrimaryClip(clip)

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (clearAfter != 0) {
            setTimer()
            showSnackbar(this.resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            showSnackbar(this.resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
         */
    }

    companion object {
        private const val KEY_PWGEN_TYPE_CLASSIC = "classic"
        private const val KEY_PWGEN_TYPE_XKPASSWD = "xkpasswd"
    }
}

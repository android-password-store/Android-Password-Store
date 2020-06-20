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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.utils.isInsideRepository
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.databinding.PasswordCreationActivityBinding
import com.zeapo.pwdstore.ui.dialogs.PasswordGeneratorDialogFragment
import com.zeapo.pwdstore.ui.dialogs.XkPasswordGeneratorDialogFragment
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.commitChange
import com.zeapo.pwdstore.utils.snackbar
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.eclipse.jgit.api.Git
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class PasswordCreationActivity : BasePgpActivity(), OpenPgpServiceConnection.OnBound {

    private val binding by viewBinding(PasswordCreationActivityBinding::inflate)

    private val suggestedName by lazy { intent.getStringExtra(EXTRA_FILE_NAME) }
    private val suggestedPass by lazy { intent.getStringExtra(EXTRA_PASSWORD) }
    private val suggestedExtra by lazy { intent.getStringExtra(EXTRA_EXTRA_CONTENT) }
    private val shouldGeneratePassword by lazy { intent.getBooleanExtra(EXTRA_GENERATE_PASSWORD, false) }
    private val editing by lazy { intent.getBooleanExtra(EXTRA_EDITING, false) }
    private val doNothing = registerForActivityResult(StartActivityForResult()) {}
    private var oldCategory: String? = null
    private val oldFileName by lazy { intent.getStringExtra(EXTRA_FILE_NAME) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this, doNothing)
        title = if (editing)
            getString(R.string.edit_password)
        else
            getString(R.string.new_password_title)
        with(binding) {
            setContentView(root)
            generatePassword.setOnClickListener { generatePassword() }

            category.apply {
                if (suggestedName != null || suggestedPass != null || shouldGeneratePassword) {
                    isEnabled = true
                } else {
                    setBackgroundColor(getColor(android.R.color.transparent))
                }
                val path = getRelativePath(fullPath, repoPath)
                // Keep empty path field visible if it is editable.
                if (path.isEmpty() && !isEnabled)
                    visibility = View.GONE
                else {
                    setText(path)
                    oldCategory = path
                }
            }
            suggestedName?.let { filename.setText(it) }
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
                            val username = filename.text.toString()
                            val extras = "username:$username\n${extraContent.text}"

                            filename.setText("")
                            extraContent.setText(extras)
                        } else {
                            // User wants to disable username encryption, so we extract the
                            // username from the encrypted extras and use it as the filename.
                            val entry = PasswordEntry("PASSWORD\n${extraContent.text}")
                            val username = entry.username

                            // username should not be null here by the logic in
                            // updateEncryptUsernameState, but it could still happen due to
                            // input lag.
                            if (username != null) {
                                filename.setText(username)
                                extraContent.setText(entry.extraContentWithoutUsername)
                            }
                        }
                        updateEncryptUsernameState()
                    }
                }
                listOf(filename, extraContent).forEach {
                    it.doOnTextChanged { _, _, _, _ -> updateEncryptUsernameState() }
                }
                updateEncryptUsernameState()
            }
            suggestedPass?.let {
                password.setText(it)
                password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            suggestedExtra?.let { extraContent.setText(it) }
            if (shouldGeneratePassword) {
                generatePassword()
                password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.pgp_handler_new_password, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home, R.id.cancel_password_add -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            R.id.save_password -> encrypt()
            R.id.save_and_copy_password -> encrypt(copy = true)
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
            val hasUsernameInFileName = filename.text.toString().isNotBlank()
            // Use PasswordEntry to parse extras for username
            val entry = PasswordEntry("PLACEHOLDER\n${extraContent.text}")
            val hasUsernameInExtras = entry.hasUsername()
            isEnabled = hasUsernameInFileName xor hasUsernameInExtras
            isChecked = hasUsernameInExtras
        }
    }

    /**
     * Encrypts the password and the extra content
     */
    private fun encrypt(copy: Boolean = false) = with(binding) {
        val editName = filename.text.toString().trim()
        val editPass = password.text.toString()
        val editExtra = extraContent.text.toString()

        if (editName.isEmpty()) {
            snackbar(message = resources.getString(R.string.file_toast_text))
            return@with
        }

        if (editPass.isEmpty() && editExtra.isEmpty()) {
            snackbar(message = resources.getString(R.string.empty_toast_text))
            return@with
        }

        if (copy) {
            copyPasswordToClipboard(editPass)
        }

        val data = Intent()
        data.action = OpenPgpApi.ACTION_ENCRYPT

        // EXTRA_KEY_IDS requires long[]
        val longKeys = keyIDs.map { it.toLong() }
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, longKeys.toLongArray())
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

        val content = "$editPass\n$editExtra"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val outputStream = ByteArrayOutputStream()

        val path = when {
            // If we allowed the user to edit the relative path, we have to consider it here instead
            // of fullPath.
            category.isEnabled -> {
                val editRelativePath = category.text.toString().trim()
                if (editRelativePath.isEmpty()) {
                    snackbar(message = resources.getString(R.string.path_toast_text))
                    return
                }
                val passwordDirectory = File("$repoPath/${editRelativePath.trim('/')}")
                if (!passwordDirectory.exists() && !passwordDirectory.mkdir()) {
                    snackbar(message = "Failed to create directory ${editRelativePath.trim('/')}")
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
                            val file = File(path)
                            // If we're not editing, this file should not already exist!
                            if (!editing && file.exists()) {
                                snackbar(message = getString(R.string.password_creation_duplicate_error))
                                return@executeApiAsync
                            }

                            if (!isInsideRepository(file)) {
                                snackbar(message = getString(R.string.message_error_destination_outside_repo))
                                return@executeApiAsync
                            }
                            try {
                                file.outputStream().use {
                                    it.write(outputStream.toByteArray())
                                }
                            } catch (e: IOException) {
                                e(e) { "Failed to write password file" }
                                setResult(RESULT_CANCELED)
                                MaterialAlertDialogBuilder(this@PasswordCreationActivity)
                                    .setTitle(getString(R.string.password_creation_file_fail_title))
                                    .setMessage(getString(R.string.password_creation_file_write_fail_message))
                                    .setCancelable(false)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        finish()
                                    }
                                    .show()
                            }

                            val returnIntent = Intent()
                            returnIntent.putExtra(RETURN_EXTRA_CREATED_FILE, path)
                            returnIntent.putExtra(RETURN_EXTRA_NAME, editName)
                            returnIntent.putExtra(RETURN_EXTRA_LONG_NAME, getLongName(fullPath, repoPath, editName))

                            if (shouldGeneratePassword) {
                                val directoryStructure =
                                    AutofillPreferences.directoryStructure(applicationContext)
                                val entry = PasswordEntry(content)
                                returnIntent.putExtra(RETURN_EXTRA_PASSWORD, entry.password)
                                val username = PasswordEntry(content).username
                                    ?: directoryStructure.getUsernameFor(file)
                                returnIntent.putExtra(RETURN_EXTRA_USERNAME, username)
                            }

                            val repo = PasswordRepository.getRepository(null)
                            if (repo != null) {
                                val status = Git(repo).status().call()
                                if (status.modified.isNotEmpty()) {
                                    commitChange(
                                        getString(
                                            R.string.git_commit_edit_text,
                                            getLongName(fullPath, repoPath, editName)
                                        )
                                    )
                                }
                            }

                            if (category.isVisible && category.isEnabled && oldFileName != null) {
                                val oldFile = File("$repoPath/${oldCategory?.trim('/')}/$oldFileName.gpg")
                                if (oldFile.path != file.path && !oldFile.delete()) {
                                    setResult(RESULT_CANCELED)
                                    MaterialAlertDialogBuilder(this@PasswordCreationActivity)
                                        .setTitle(R.string.password_creation_file_fail_title)
                                        .setMessage(getString(R.string.password_creation_file_delete_fail_message, oldFileName))
                                        .setCancelable(false)
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            finish()
                                        }
                                        .show()
                                } else {
                                    setResult(RESULT_OK, returnIntent)
                                    finish()
                                }
                            } else {
                                setResult(RESULT_OK, returnIntent)
                                finish()
                            }

                        } catch (e: Exception) {
                            e(e) { "An Exception occurred" }
                        }
                    }
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }

    companion object {
        private const val KEY_PWGEN_TYPE_CLASSIC = "classic"
        private const val KEY_PWGEN_TYPE_XKPASSWD = "xkpasswd"
        const val RETURN_EXTRA_CREATED_FILE = "CREATED_FILE"
        const val RETURN_EXTRA_NAME = "NAME"
        const val RETURN_EXTRA_LONG_NAME = "LONG_NAME"
        const val RETURN_EXTRA_USERNAME = "USERNAME"
        const val RETURN_EXTRA_PASSWORD = "PASSWORD"
        const val EXTRA_FILE_NAME = "FILENAME"
        const val EXTRA_PASSWORD = "PASSWORD"
        const val EXTRA_EXTRA_CONTENT = "EXTRA_CONTENT"
        const val EXTRA_GENERATE_PASSWORD = "GENERATE_PASSWORD"
        const val EXTRA_EDITING = "EDITING"
    }
}

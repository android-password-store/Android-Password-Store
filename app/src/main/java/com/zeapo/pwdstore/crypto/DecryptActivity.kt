/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.DecryptLayoutBinding
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2
import java.io.ByteArrayOutputStream
import java.io.File

class DecryptActivity : BasePgpActivity(R.layout.decrypt_layout), OpenPgpServiceConnection.OnBound {
    private val binding by viewBinding(DecryptLayoutBinding::inflate)

    private val relativeParentPath by lazy { getParentPath(fullPath, repoPath) }
    private var passwordEntry: PasswordEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this)
        with(binding) {
            setContentView(root)
            passwordCategory.text = relativeParentPath
            passwordFile.text = name
            passwordFile.setOnLongClickListener {
                copyTextToClipboard(name)
                true
            }
            try {
                passwordLastChanged.text = resources.getString(R.string.last_changed, lastChangedString)
            } catch (e: RuntimeException) {
                passwordLastChanged.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.pgp_handler, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            R.id.share_password_as_plaintext -> shareAsPlaintext()
            R.id.copy_password -> copyPasswordToClipboard()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return
        }

        // try again after user interaction
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_DECRYPT -> decryptAndVerify(data)
                else -> {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data)
            finish()
        }
    }

    override fun onBound(service: IOpenPgpService2) {
        super.onBound(service)
        decryptAndVerify()
    }

    override fun onError(e: Exception) {
        e(e)
    }

    private fun copyTextToClipboard(text: String?, showSnackbar: Boolean = true) {
        val clipboard = clipboard ?: return
        val clip = ClipData.newPlainText("pgp_handler_result_pm", text)
        clipboard.setPrimaryClip(clip)
        if (showSnackbar) {
            showSnackbar(resources.getString(R.string.clipboard_copied_text))
        }
    }

    private fun copyPasswordToClipboard() {
        copyTextToClipboard(passwordEntry?.password, showSnackbar = false)

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (clearAfter != 0) {
            //setTimer()
            showSnackbar(this.resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            showSnackbar(this.resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
    }

    private fun shareAsPlaintext() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
            type = "text/plain"
        }
        // Always show a picker to give the user a chance to cancel
        startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_plaintext_password_to)))
    }

    private fun decryptAndVerify(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_DECRYPT_VERIFY

        val inputStream = File(fullPath).inputStream()
        val outputStream = ByteArrayOutputStream()

        lifecycleScope.launch(Dispatchers.IO) {
            api?.executeApiAsync(data, inputStream, outputStream) { result ->
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            val showPassword = settings.getBoolean("show_password", true)
                            val showExtraContent = settings.getBoolean("show_extra_content", true)
                            val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
                            val entry = PasswordEntry(outputStream)

                            passwordEntry = entry

                            with(binding) {
                                if (entry.password.isEmpty()) {
                                    passwordTextContainer.visibility = View.GONE
                                } else {
                                    passwordTextContainer.visibility = View.VISIBLE
                                    if (!showPassword) {
                                        passwordText.transformationMethod = PasswordTransformationMethod.getInstance()
                                    }
                                    passwordTextContainer.setOnClickListener { copyPasswordToClipboard() }
                                    passwordText.setOnClickListener { copyPasswordToClipboard() }
                                }

                                if (entry.hasExtraContent()) {
                                    extraContentContainer.visibility = View.VISIBLE
                                    extraContent.typeface = monoTypeface
                                    extraContent.setText(entry.extraContentWithoutUsername)
                                    if (!showExtraContent) {
                                        extraContent.transformationMethod = PasswordTransformationMethod.getInstance()
                                    }
                                    extraContentContainer.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutUsername) }
                                    extraContent.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutUsername) }

                                    if (entry.hasUsername()) {
                                        usernameText.typeface = monoTypeface
                                        usernameText.setText(entry.username)
                                        usernameTextContainer.setEndIconOnClickListener { copyTextToClipboard(entry.username) }
                                        usernameTextContainer.visibility = View.VISIBLE
                                    } else {
                                        usernameTextContainer.visibility = View.GONE
                                    }
                                }
                            }

                            if (settings.getBoolean("copy_on_decrypt", true)) {
                                copyPasswordToClipboard()
                            }
                        } catch (e: Exception) {
                            e(e)
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_DECRYPT)
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }
}

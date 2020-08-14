/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.DecryptLayoutBinding
import com.zeapo.pwdstore.model.PasswordEntry
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.viewBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2

class DecryptActivity : BasePgpActivity(), OpenPgpServiceConnection.OnBound {

    private val binding by viewBinding(DecryptLayoutBinding::inflate)

    private val relativeParentPath by lazy { getParentPath(fullPath, repoPath) }
    private var passwordEntry: PasswordEntry? = null

    private val userInteractionRequiredResult = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return@registerForActivityResult
        }

        when (result.resultCode) {
            RESULT_OK -> decryptAndVerify(result.data)
            RESULT_CANCELED -> {
                setResult(RESULT_CANCELED, result.data)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this)
        title = name
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
            R.id.edit_password -> editPassword()
            R.id.share_password_as_plaintext -> shareAsPlaintext()
            R.id.copy_password -> copyPasswordToClipboard(passwordEntry?.password)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBound(service: IOpenPgpService2) {
        super.onBound(service)
        decryptAndVerify()
    }

    override fun onError(e: Exception) {
        e(e)
    }

    /**
     * Edit the current password and hide all the fields populated by encrypted data so that when
     * the result triggers they can be repopulated with new data.
     */
    private fun editPassword() {
        val intent = Intent(this, PasswordCreationActivity::class.java)
        intent.putExtra("FILE_PATH", relativeParentPath)
        intent.putExtra("REPO_PATH", repoPath)
        intent.putExtra(PasswordCreationActivity.EXTRA_FILE_NAME, name)
        intent.putExtra(PasswordCreationActivity.EXTRA_PASSWORD, passwordEntry?.password)
        intent.putExtra(PasswordCreationActivity.EXTRA_EXTRA_CONTENT, passwordEntry?.extraContent)
        intent.putExtra(PasswordCreationActivity.EXTRA_EDITING, true)
        startActivity(intent)
        finish()
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

    @OptIn(ExperimentalTime::class)
    private fun decryptAndVerify(receivedIntent: Intent? = null) {
        if (api == null) {
            bindToOpenKeychain(this)
            return
        }
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_DECRYPT_VERIFY

        val inputStream = try {
            File(fullPath).inputStream()
        } catch (e: FileNotFoundException) {
            Toast.makeText(this, getString(R.string.error_broken_symlink), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val outputStream = ByteArrayOutputStream()

        lifecycleScope.launch(Dispatchers.IO) {
            api?.executeApiAsync(data, inputStream, outputStream) { result ->
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
                            val showExtraContent = settings.getBoolean(PreferenceKeys.SHOW_EXTRA_CONTENT, true)
                            val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
                            val entry = PasswordEntry(outputStream)

                            passwordEntry = entry

                            with(binding) {
                                if (entry.password.isEmpty()) {
                                    passwordTextContainer.visibility = View.GONE
                                } else {
                                    passwordTextContainer.visibility = View.VISIBLE
                                    passwordText.typeface = monoTypeface
                                    passwordText.setText(entry.password)
                                    if (!showPassword) {
                                        passwordText.transformationMethod = PasswordTransformationMethod.getInstance()
                                    }
                                    passwordTextContainer.setOnClickListener { copyPasswordToClipboard(entry.password) }
                                    passwordText.setOnClickListener { copyPasswordToClipboard(entry.password) }
                                }

                                if (entry.hasExtraContent()) {
                                    if (entry.extraContentWithoutAuthData.isNotEmpty()) {
                                        extraContentContainer.visibility = View.VISIBLE
                                        extraContent.typeface = monoTypeface
                                        extraContent.setText(entry.extraContentWithoutAuthData)
                                        if (!showExtraContent) {
                                            extraContent.transformationMethod = PasswordTransformationMethod.getInstance()
                                        }
                                        extraContentContainer.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutAuthData) }
                                        extraContent.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutAuthData) }
                                    }

                                    if (entry.hasUsername()) {
                                        usernameText.typeface = monoTypeface
                                        usernameText.setText(entry.username)
                                        usernameTextContainer.setEndIconOnClickListener { copyTextToClipboard(entry.username) }
                                        usernameTextContainer.visibility = View.VISIBLE
                                    } else {
                                        usernameTextContainer.visibility = View.GONE
                                    }

                                    if (entry.hasTotp()) {
                                        otpTextContainer.visibility = View.VISIBLE
                                        otpTextContainer.setEndIconOnClickListener {
                                            copyTextToClipboard(
                                                otpText.text.toString(),
                                                snackbarTextRes = R.string.clipboard_otp_copied_text
                                            )
                                        }
                                        launch(Dispatchers.IO) {
                                            repeat(Int.MAX_VALUE) {
                                                val code = entry.calculateTotpCode() ?: "Error"
                                                withContext(Dispatchers.Main) {
                                                    otpText.setText(code)
                                                }
                                                delay(30.seconds)
                                            }
                                        }
                                    }
                                }
                            }

                            if (settings.getBoolean(PreferenceKeys.COPY_ON_DECRYPT, false)) {
                                copyPasswordToClipboard(entry.password)
                            }
                        } catch (e: Exception) {
                            e(e)
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                        val sender = getUserInteractionRequestIntent(result)
                        userInteractionRequiredResult.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }
}

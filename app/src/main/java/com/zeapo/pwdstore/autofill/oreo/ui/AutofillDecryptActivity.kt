/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.autofill.oreo.AutofillAction
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.Credentials
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.autofill.oreo.FillableForm
import com.zeapo.pwdstore.model.PasswordEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.O)
class AutofillDecryptActivity : Activity(), CoroutineScope {

    companion object {
        private const val EXTRA_FILE_PATH = "com.zeapo.pwdstore.autofill.oreo.EXTRA_FILE_PATH"
        private const val EXTRA_SEARCH_ACTION =
            "com.zeapo.pwdstore.autofill.oreo.EXTRA_SEARCH_ACTION"
        private const val REQUEST_CODE_CONTINUE_AFTER_USER_INTERACTION = 1
        private const val OPENPGP_PROVIDER = "org.sufficientlysecure.keychain"

        private var decryptFileRequestCode = 1

        fun makeDecryptFileIntent(file: File, forwardedExtras: Bundle, context: Context): Intent {
            return Intent(context, AutofillDecryptActivity::class.java).apply {
                putExtras(forwardedExtras)
                putExtra(EXTRA_SEARCH_ACTION, true)
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
        }

        fun makeDecryptFileIntentSender(file: File, context: Context): IntentSender {
            val intent = Intent(context, AutofillDecryptActivity::class.java).apply {
                putExtra(EXTRA_SEARCH_ACTION, false)
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
            return PendingIntent.getActivity(
                context,
                decryptFileRequestCode++,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }
    }

    private var continueAfterUserInteraction: Continuation<Intent>? = null
    private lateinit var directoryStructure: DirectoryStructure

    override val coroutineContext
        get() = Dispatchers.IO + SupervisorJob()

    override fun onStart() {
        super.onStart()
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH) ?: run {
            e { "AutofillDecryptActivity started without EXTRA_FILE_PATH" }
            finish()
            return
        }
        val clientState = intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE) ?: run {
            e { "AutofillDecryptActivity started without EXTRA_CLIENT_STATE" }
            finish()
            return
        }
        val isSearchAction = intent?.getBooleanExtra(EXTRA_SEARCH_ACTION, true)!!
        val action = if (isSearchAction) AutofillAction.Search else AutofillAction.Match
        directoryStructure = AutofillPreferences.directoryStructure(this)
        d { action.toString() }
        launch {
            val credentials = decryptCredential(File(filePath))
            if (credentials == null) {
                setResult(RESULT_CANCELED)
            } else {
                val fillInDataset =
                    FillableForm.makeFillInDataset(
                        this@AutofillDecryptActivity,
                        credentials,
                        clientState,
                        action
                    )
                withContext(Dispatchers.Main) {
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset)
                    })
                }
            }
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

    private suspend fun executeOpenPgpApi(
        data: Intent,
        input: InputStream,
        output: OutputStream
    ): Intent? {
        var openPgpServiceConnection: OpenPgpServiceConnection? = null
        val openPgpService = suspendCoroutine<IOpenPgpService2> { cont ->
            openPgpServiceConnection = OpenPgpServiceConnection(
                this,
                OPENPGP_PROVIDER,
                object : OpenPgpServiceConnection.OnBound {
                    override fun onBound(service: IOpenPgpService2) {
                        cont.resume(service)
                    }

                    override fun onError(e: Exception) {
                        cont.resumeWithException(e)
                    }
                }).also { it.bindToService() }
        }
        return OpenPgpApi(this, openPgpService).executeApi(data, input, output).also {
            openPgpServiceConnection?.unbindFromService()
        }
    }

    private suspend fun decryptCredential(
        file: File,
        resumeIntent: Intent? = null
    ): Credentials? {
        val command = resumeIntent ?: Intent().apply {
            action = OpenPgpApi.ACTION_DECRYPT_VERIFY
        }
        val encryptedInput = try {
            file.inputStream()
        } catch (e: FileNotFoundException) {
            e(e) { "File to decrypt not found" }
            return null
        }
        val decryptedOutput = ByteArrayOutputStream()
        val result = try {
            executeOpenPgpApi(command, encryptedInput, decryptedOutput)
        } catch (e: Exception) {
            e(e) { "OpenPgpApi ACTION_DECRYPT_VERIFY failed" }
            return null
        }
        return when (val resultCode =
            result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                try {
                    val entry = withContext(Dispatchers.IO) {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        PasswordEntry(decryptedOutput)
                    }
                    Credentials.fromStoreEntry(this, file, entry, directoryStructure)
                } catch (e: UnsupportedEncodingException) {
                    e(e) { "Failed to parse password entry" }
                    null
                }
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                val pendingIntent: PendingIntent =
                    result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)!!
                try {
                    val intentToResume = withContext(Dispatchers.Main) {
                        suspendCoroutine<Intent> { cont ->
                            continueAfterUserInteraction = cont
                            startIntentSenderForResult(
                                pendingIntent.intentSender,
                                REQUEST_CODE_CONTINUE_AFTER_USER_INTERACTION,
                                null,
                                0,
                                0,
                                0
                            )
                        }
                    }
                    decryptCredential(file, intentToResume)
                } catch (e: Exception) {
                    e(e) { "OpenPgpApi ACTION_DECRYPT_VERIFY failed with user interaction" }
                    null
                }
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                if (error != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Error from OpenKeyChain: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    e { "OpenPgpApi ACTION_DECRYPT_VERIFY failed (${error.errorId}): ${error.message}" }
                }
                null
            }
            else -> {
                e { "Unrecognized OpenPgpApi result: $resultCode" }
                null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONTINUE_AFTER_USER_INTERACTION && continueAfterUserInteraction != null) {
            if (resultCode == RESULT_OK && data != null) {
                continueAfterUserInteraction?.resume(data)
            } else {
                continueAfterUserInteraction?.resumeWithException(Exception("OpenPgpApi ACTION_DECRYPT_VERIFY failed to continue after user interaction"))
            }
            continueAfterUserInteraction = null
        }
    }
}

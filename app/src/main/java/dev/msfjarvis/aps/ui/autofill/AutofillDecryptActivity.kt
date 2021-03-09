/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.data.password.PasswordEntry
import dev.msfjarvis.aps.util.autofill.AutofillPreferences
import dev.msfjarvis.aps.util.autofill.AutofillResponseBuilder
import dev.msfjarvis.aps.util.autofill.DirectoryStructure
import dev.msfjarvis.aps.util.extensions.OPENPGP_PROVIDER
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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

@RequiresApi(Build.VERSION_CODES.O)
class AutofillDecryptActivity : AppCompatActivity(), CoroutineScope {

    companion object {

        private const val EXTRA_FILE_PATH = "dev.msfjarvis.aps.autofill.oreo.EXTRA_FILE_PATH"
        private const val EXTRA_SEARCH_ACTION =
            "dev.msfjarvis.aps.autofill.oreo.EXTRA_SEARCH_ACTION"

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

    private val decryptInteractionRequiredAction = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (continueAfterUserInteraction != null) {
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                continueAfterUserInteraction?.resume(data)
            } else {
                continueAfterUserInteraction?.resumeWithException(Exception("OpenPgpApi ACTION_DECRYPT_VERIFY failed to continue after user interaction"))
            }
            continueAfterUserInteraction = null
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
                    AutofillResponseBuilder.makeFillInDataset(
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
        runCatching {
            file.inputStream()
        }.onFailure { e ->
            e(e) { "File to decrypt not found" }
            return null
        }.onSuccess { encryptedInput ->
            val decryptedOutput = ByteArrayOutputStream()
            runCatching {
                executeOpenPgpApi(command, encryptedInput, decryptedOutput)
            }.onFailure { e ->
                e(e) { "OpenPgpApi ACTION_DECRYPT_VERIFY failed" }
                return null
            }.onSuccess { result ->
                return when (val resultCode =
                    result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        runCatching {
                            val entry = withContext(Dispatchers.IO) {
                                @Suppress("BlockingMethodInNonBlockingContext")
                                (PasswordEntry(decryptedOutput))
                            }
                            AutofillPreferences.credentialsFromStoreEntry(this, file, entry, directoryStructure)
                        }.getOrElse { e ->
                            e(e) { "Failed to parse password entry" }
                            return null
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                        val pendingIntent: PendingIntent =
                            result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)!!
                        runCatching {
                            val intentToResume = withContext(Dispatchers.Main) {
                                suspendCoroutine<Intent> { cont ->
                                    continueAfterUserInteraction = cont
                                    decryptInteractionRequiredAction.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                }
                            }
                            decryptCredential(file, intentToResume)
                        }.getOrElse { e ->
                            e(e) { "OpenPgpApi ACTION_DECRYPT_VERIFY failed with user interaction" }
                            return null
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
        }
        return null
    }
}

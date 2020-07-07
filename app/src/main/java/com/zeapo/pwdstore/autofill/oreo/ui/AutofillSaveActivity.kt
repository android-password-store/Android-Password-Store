/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.oreo.AutofillAction
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.Credentials
import com.zeapo.pwdstore.autofill.oreo.FillableForm
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.crypto.PasswordCreationActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.commitChange
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
class AutofillSaveActivity : AppCompatActivity() {

    companion object {

        private const val EXTRA_FOLDER_NAME =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_FOLDER_NAME"
        private const val EXTRA_PASSWORD = "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_PASSWORD"
        private const val EXTRA_NAME = "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_NAME"
        private const val EXTRA_SHOULD_MATCH_APP =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_SHOULD_MATCH_APP"
        private const val EXTRA_SHOULD_MATCH_WEB =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_SHOULD_MATCH_WEB"
        private const val EXTRA_GENERATE_PASSWORD =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_GENERATE_PASSWORD"

        private var saveRequestCode = 1

        fun makeSaveIntentSender(
            context: Context,
            credentials: Credentials?,
            formOrigin: FormOrigin
        ): IntentSender {
            val identifier = formOrigin.getPrettyIdentifier(context, untrusted = false)
            // Prevent directory traversals
            val sanitizedIdentifier = identifier.replace('\\', '_')
                .replace('/', '_')
                .trimStart('.')
                .takeUnless { it.isBlank() } ?: formOrigin.identifier
            val directoryStructure = AutofillPreferences.directoryStructure(context)
            val folderName = directoryStructure.getSaveFolderName(
                sanitizedIdentifier = sanitizedIdentifier,
                username = credentials?.username
            )
            val fileName = directoryStructure.getSaveFileName(username = credentials?.username, identifier = identifier)
            val intent = Intent(context, AutofillSaveActivity::class.java).apply {
                putExtras(
                    bundleOf(
                        EXTRA_FOLDER_NAME to folderName,
                        EXTRA_NAME to fileName,
                        EXTRA_PASSWORD to credentials?.password,
                        EXTRA_SHOULD_MATCH_APP to formOrigin.identifier.takeIf { formOrigin is FormOrigin.App },
                        EXTRA_SHOULD_MATCH_WEB to formOrigin.identifier.takeIf { formOrigin is FormOrigin.Web },
                        EXTRA_GENERATE_PASSWORD to (credentials == null)
                    )
                )
            }
            return PendingIntent.getActivity(
                context,
                saveRequestCode++,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }
    }

    private val formOrigin: FormOrigin? by lazy {
        val shouldMatchApp: String? = intent.getStringExtra(EXTRA_SHOULD_MATCH_APP)
        val shouldMatchWeb: String? = intent.getStringExtra(EXTRA_SHOULD_MATCH_WEB)
        if (shouldMatchApp != null && shouldMatchWeb == null) {
            FormOrigin.App(shouldMatchApp)
        } else if (shouldMatchApp == null && shouldMatchWeb != null) {
            FormOrigin.Web(shouldMatchWeb)
        } else {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = PasswordRepository.getRepositoryDirectory(applicationContext)
        val saveIntent = Intent(this, PasswordCreationActivity::class.java).apply {
            putExtras(
                bundleOf(
                    "REPO_PATH" to repo.absolutePath,
                    "FILE_PATH" to repo.resolve(intent.getStringExtra(EXTRA_FOLDER_NAME)!!).absolutePath,
                    PasswordCreationActivity.EXTRA_FILE_NAME to intent.getStringExtra(EXTRA_NAME),
                    PasswordCreationActivity.EXTRA_PASSWORD to intent.getStringExtra(EXTRA_PASSWORD),
                    PasswordCreationActivity.EXTRA_GENERATE_PASSWORD to intent.getBooleanExtra(EXTRA_GENERATE_PASSWORD, false)
                )
            )
        }
        registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val createdPath = data.getStringExtra("CREATED_FILE")!!
                formOrigin?.let {
                    AutofillMatcher.addMatchFor(this, it, File(createdPath))
                }
                val longName = data.getStringExtra("LONG_NAME")!!
                val password = data.getStringExtra("PASSWORD")
                val resultIntent = if (password != null) {
                    // Password was generated and should be filled into a form.
                    val username = data.getStringExtra("USERNAME")
                    val clientState =
                        intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE) ?: run {
                            e { "AutofillDecryptActivity started without EXTRA_CLIENT_STATE" }
                            finish()
                            return@registerForActivityResult
                        }
                    val credentials = Credentials(username, password, null)
                    val fillInDataset = FillableForm.makeFillInDataset(
                        this,
                        credentials,
                        clientState,
                        AutofillAction.Generate
                    )
                    Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset)
                    }
                } else {
                    // Password was extracted from a form, there is nothing to fill.
                    Intent()
                }
                // PasswordCreationActivity delegates committing the added file to PasswordStore. Since
                // PasswordStore is not involved in an AutofillScenario, we have to commit the file ourselves.
                commitChange(
                    getString(R.string.git_commit_add_text, longName),
                    finishWithResultOnEnd = resultIntent
                )
                // GitAsyncTask will finish the activity for us.
            }
        }.launch(saveIntent)
    }
}

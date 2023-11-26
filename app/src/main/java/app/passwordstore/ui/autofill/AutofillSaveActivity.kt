/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.crypto.PasswordCreationActivity
import app.passwordstore.util.autofill.AutofillMatcher
import app.passwordstore.util.autofill.AutofillPreferences
import app.passwordstore.util.autofill.AutofillResponseBuilder
import app.passwordstore.util.extensions.unsafeLazy
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.androidpasswordstore.autofillparser.FormOrigin
import dagger.hilt.android.AndroidEntryPoint
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import logcat.LogPriority.ERROR
import logcat.logcat

@AndroidEntryPoint
class AutofillSaveActivity : AppCompatActivity() {

  companion object {

    private const val EXTRA_FOLDER_NAME = "app.passwordstore.autofill.oreo.ui.EXTRA_FOLDER_NAME"
    private const val EXTRA_PASSWORD = "app.passwordstore.autofill.oreo.ui.EXTRA_PASSWORD"
    private const val EXTRA_NAME = "app.passwordstore.autofill.oreo.ui.EXTRA_NAME"
    private const val EXTRA_SHOULD_MATCH_APP =
      "app.passwordstore.autofill.oreo.ui.EXTRA_SHOULD_MATCH_APP"
    private const val EXTRA_SHOULD_MATCH_WEB =
      "app.passwordstore.autofill.oreo.ui.EXTRA_SHOULD_MATCH_WEB"
    private const val EXTRA_GENERATE_PASSWORD =
      "app.passwordstore.autofill.oreo.ui.EXTRA_GENERATE_PASSWORD"

    private var saveRequestCode = 1

    fun makeSaveIntentSender(
      context: Context,
      credentials: Credentials?,
      formOrigin: FormOrigin
    ): IntentSender {
      val identifier = formOrigin.getPrettyIdentifier(context, untrusted = false)
      // Prevent directory traversals
      val sanitizedIdentifier =
        identifier.replace('\\', '_').replace('/', '_').trimStart('.').takeUnless { it.isBlank() }
          ?: formOrigin.identifier
      val directoryStructure = AutofillPreferences.directoryStructure(context)
      val folderName =
        directoryStructure.getSaveFolderName(
          sanitizedIdentifier = sanitizedIdentifier,
          username = credentials?.username
        )
      val fileName =
        directoryStructure.getSaveFileName(
          username = credentials?.username,
          identifier = identifier
        )
      val intent =
        Intent(context, AutofillSaveActivity::class.java).apply {
          putExtras(
            bundleOf(
              EXTRA_FOLDER_NAME to folderName,
              EXTRA_NAME to fileName,
              EXTRA_PASSWORD to credentials?.password,
              EXTRA_SHOULD_MATCH_APP to
                formOrigin.identifier.takeIf { formOrigin is FormOrigin.App },
              EXTRA_SHOULD_MATCH_WEB to
                formOrigin.identifier.takeIf { formOrigin is FormOrigin.Web },
              EXTRA_GENERATE_PASSWORD to (credentials == null)
            )
          )
        }
      return PendingIntent.getActivity(
          context,
          saveRequestCode++,
          intent,
          PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        .intentSender
    }
  }

  private val formOrigin by unsafeLazy {
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
    val repo = PasswordRepository.getRepositoryDirectory()
    val saveIntent =
      Intent(this, PasswordCreationActivity::class.java).apply {
        putExtras(
          bundleOf(
            "REPO_PATH" to repo.absolutePathString(),
            "FILE_PATH" to
              repo.resolve(intent.getStringExtra(EXTRA_FOLDER_NAME)!!).absolutePathString(),
            PasswordCreationActivity.EXTRA_FILE_NAME to intent.getStringExtra(EXTRA_NAME),
            PasswordCreationActivity.EXTRA_PASSWORD to intent.getStringExtra(EXTRA_PASSWORD),
            PasswordCreationActivity.EXTRA_GENERATE_PASSWORD to
              intent.getBooleanExtra(EXTRA_GENERATE_PASSWORD, false)
          )
        )
      }
    registerForActivityResult(StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
          val createdPath = data.getStringExtra("CREATED_FILE")!!
          formOrigin?.let { AutofillMatcher.addMatchFor(this, it, Paths.get(createdPath)) }
          val password = data.getStringExtra("PASSWORD")
          val resultIntent =
            if (password != null) {
              // Password was generated and should be filled into a form.
              val username = data.getStringExtra("USERNAME")
              val clientState =
                intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE)
                  ?: run {
                    logcat(ERROR) { "AutofillDecryptActivity started without EXTRA_CLIENT_STATE" }
                    finish()
                    return@registerForActivityResult
                  }
              val credentials = Credentials(username, password, null)
              val fillInDataset =
                AutofillResponseBuilder.makeFillInDataset(
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
          setResult(RESULT_OK, resultIntent)
        } else {
          setResult(RESULT_CANCELED)
        }
        finish()
      }
      .launch(saveIntent)
  }
}

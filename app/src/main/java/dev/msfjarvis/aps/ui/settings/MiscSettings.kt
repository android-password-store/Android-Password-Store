/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import dev.msfjarvis.aps.BuildConfig
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.services.PasswordExportService
import dev.msfjarvis.aps.util.settings.PreferenceKeys

class MiscSettings(activity: FragmentActivity) : SettingsProvider {

  private val storeExportAction =
    activity.registerForActivityResult(
      object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
          return super.createIntent(context, input).apply {
            flags =
              Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
          }
        }
      }
    ) { uri: Uri? ->
      if (uri == null) return@registerForActivityResult
      val targetDirectory = DocumentFile.fromTreeUri(activity.applicationContext, uri)

      if (targetDirectory != null) {
        val service =
          Intent(activity.applicationContext, PasswordExportService::class.java).apply {
            action = PasswordExportService.ACTION_EXPORT_PASSWORD
            putExtra("uri", uri)
          }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          activity.startForegroundService(service)
        } else {
          activity.startService(service)
        }
      }
    }

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    builder.apply {
      pref(PreferenceKeys.EXPORT_PASSWORDS) {
        titleRes = R.string.prefs_export_passwords_title
        summaryRes = R.string.prefs_export_passwords_summary
        onClick {
          storeExportAction.launch(null)
          true
        }
      }
      checkBox(PreferenceKeys.CLEAR_CLIPBOARD_HISTORY) {
        defaultValue = false
        titleRes = R.string.pref_clear_clipboard_title
        summaryRes = R.string.pref_clear_clipboard_summary
      }
      checkBox(PreferenceKeys.ENABLE_DEBUG_LOGGING) {
        defaultValue = false
        titleRes = R.string.pref_debug_logging_title
        summaryRes = R.string.pref_debug_logging_summary
        visible = !BuildConfig.DEBUG
      }
    }
  }
}

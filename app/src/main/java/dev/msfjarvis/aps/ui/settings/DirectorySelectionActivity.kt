/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import com.github.ajalt.timberkt.d
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DirectorySelectionActivity : AppCompatActivity() {

    @Suppress("DEPRECATION")
    private val directorySelectAction = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        d { "Selected repository URI is $uri" }
        // TODO: This is fragile. Workaround until PasswordItem is backed by DocumentFile
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val path = if (split.size > 1) split[1] else split[0]
        val repoPath = "${Environment.getExternalStorageDirectory()}/$path"
        val prefs = sharedPrefs

        d { "Selected repository path is $repoPath" }

        if (Environment.getExternalStorageDirectory().path == repoPath) {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.sdcard_root_warning_title))
                .setMessage(resources.getString(R.string.sdcard_root_warning_message))
                .setPositiveButton(resources.getString(R.string.sdcard_root_warning_remove_everything)) { _, _ ->
                    prefs.edit { putString(PreferenceKeys.GIT_EXTERNAL_REPO, uri.path) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
        prefs.edit { putString(PreferenceKeys.GIT_EXTERNAL_REPO, repoPath) }
        setResult(RESULT_OK)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directorySelectAction.launch(null)
    }
}

/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.fragment.app.Fragment

object SAFUtils {
  const val REQUEST_OPEN_DOCUMENT_TREE = 501

  fun openDirectory(activity: Activity, pickerInitialUri: Uri?) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      if (pickerInitialUri is Uri && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
      }
    }

    activity.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE, null)
  }

  fun openDirectory(fragment: Fragment, pickerInitialUri: Uri?) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      if (pickerInitialUri is Uri && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
      }
    }

    fragment.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE, null)
  }

  fun makeUriPersistable(context: Context, uri: Uri) {
    val appContext = context.applicationContext
    val contentResolver = appContext.contentResolver
    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    contentResolver.takePersistableUriPermission(uri, takeFlags)
  }
}
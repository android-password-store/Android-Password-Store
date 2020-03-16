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
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment

object SAFUtils {
  const val REQUEST_OPEN_DOCUMENT_TREE = 501

  private const val persistableFlags =
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

  fun Activity.openDirectory(persistUri: Boolean, pickerInitialUri: Uri? = null) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or persistableFlags
      if (persistUri) {
        flags.or(persistableFlags)
      }
      if (pickerInitialUri is Uri && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
      }
    }

    this.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE, null)
  }

  fun Fragment.openDirectory(persistUri: Boolean, pickerInitialUri: Uri? = null) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      if (persistUri) {
        flags.or(persistableFlags)
      }
      if (pickerInitialUri is Uri && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
      }
    }

    this.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE, null)
  }

  fun Context.makeUriPersistable(uri: Uri) {
    val contentResolver = applicationContext.contentResolver
    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    contentResolver.takePersistableUriPermission(uri, takeFlags)
  }

  fun Activity.makeUriPersistable(uri: Uri) {
    val contentResolver = applicationContext.contentResolver
    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    contentResolver.takePersistableUriPermission(uri, takeFlags)
  }

  fun Fragment.makeUriPersistable(uri: Uri) {
    val contentResolver = requireContext().applicationContext.contentResolver
    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    contentResolver.takePersistableUriPermission(uri, takeFlags)
  }

  fun documentFileFromUri(context: Context, uri: Uri): DocumentFile? {
    return if (uri.toString().startsWith("file")) {
      DocumentFile.fromFile(uri.toFile())
    } else {
      DocumentFile.fromTreeUri(context, uri)
    }
  }
}

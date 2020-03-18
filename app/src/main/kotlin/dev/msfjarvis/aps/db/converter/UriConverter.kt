/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.db.converter

import android.net.Uri
import androidx.room.TypeConverter

class UriConverter {
  @TypeConverter
  fun stringToUri(value: String?): Uri? {
    return if (value == null) null else Uri.parse(value)
  }

  @TypeConverter
  fun uriToString(uri: Uri?): String? {
    return uri.toString()
  }
}

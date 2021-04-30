/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("BlockingMethodInNonBlockingContext")

package me.msfjarvis.openpgpktx.util

import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ParcelFileDescriptorUtil {

  private const val TAG = "PFDUtils"

  internal suspend fun pipeFrom(inputStream: InputStream): ParcelFileDescriptor {
    val pipe = ParcelFileDescriptor.createPipe()
    val readSide = pipe[0]
    val writeSide = pipe[1]
    transferStreams(inputStream, AutoCloseOutputStream(writeSide))
    return readSide
  }

  internal suspend fun pipeTo(outputStream: OutputStream, output: ParcelFileDescriptor?) {
    transferStreams(AutoCloseInputStream(output), outputStream)
  }

  private suspend fun transferStreams(`in`: InputStream, `out`: OutputStream) {
    withContext(Dispatchers.IO) {
      val buf = ByteArray(4096)
      var len: Int
      try {
        while (`in`.read(buf).also { len = it } > 0) {
          out.write(buf, 0, len)
        }
      } catch (e: IOException) {
        Log.e(TAG, "IOException when writing to out", e)
      } finally {
        try {
          `in`.close()
        } catch (ignored: IOException) {}
        try {
          out.close()
        } catch (ignored: IOException) {}
      }
    }
  }
}

/*
 * SPDX-License-Identifier: (LGPL-3.0-only WITH LGPL-3.0-linking-exception) OR MPL-2.0
 */

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.lib.publicsuffixlist

import android.content.Context
import java.io.BufferedInputStream
import java.io.IOException

private const val PUBLIC_SUFFIX_LIST_FILE = "publicsuffixes"

internal object PublicSuffixListLoader {

  fun load(inputStream: BufferedInputStream): PublicSuffixListData =
    inputStream.use { stream ->
      val publicSuffixSize = stream.readInt()
      val publicSuffixBytes = stream.readFully(publicSuffixSize)

      val exceptionSize = stream.readInt()
      val exceptionBytes = stream.readFully(exceptionSize)

      PublicSuffixListData(publicSuffixBytes, exceptionBytes)
    }

  fun load(context: Context): PublicSuffixListData =
    load(context.assets.open(PUBLIC_SUFFIX_LIST_FILE).buffered())
}

@Suppress("MagicNumber")
private fun BufferedInputStream.readInt(): Int {
  return (read() and
    0xff shl
    24 or
    (read() and 0xff shl 16) or
    (read() and 0xff shl 8) or
    (read() and 0xff))
}

private fun BufferedInputStream.readFully(size: Int): ByteArray {
  val bytes = ByteArray(size)

  var offset = 0
  while (offset < size) {
    val read = read(bytes, offset, size - offset)
    if (read == -1) {
      throw IOException("Unexpected end of stream")
    }
    offset += read
  }

  return bytes
}

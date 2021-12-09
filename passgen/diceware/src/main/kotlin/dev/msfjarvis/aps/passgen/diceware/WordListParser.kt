/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.passgen.diceware

import java.io.InputStream

internal object WordListParser {
  fun parse(wordlistStream: InputStream) =
    wordlistStream
      .bufferedReader()
      .lineSequence()
      .map { line -> line.split(DELIMITER) }
      .filter { items -> items.size == 2 && items[0].toIntOrNull() != null }
      .map { items -> items[0].toInt() to items[1] }
      .toMap()

  private const val DELIMITER = "\t"
}

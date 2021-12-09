/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.passgen.diceware

import kotlin.test.Test
import kotlin.test.assertEquals

class WordListParserTest {
  @Test
  fun parseWordList() {
    val stream = "11111\tabcde\n22222\tfghij".byteInputStream()
    val parsedMap = WordListParser.parse(stream)
    assertEquals(2, parsedMap.size)
    assertEquals("abcde", parsedMap[11111])
    assertEquals("fghij", parsedMap[22222])
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import kotlin.test.Test

class AlphabetModifierTest {

  @Test
  fun `adds specified number of alphabets`() {
    val modifier = AlphabetModifier(10)
    modifier.check(emptyArray(), 10)
  }

  @Test
  fun `all added entries are alphabets`() {
    val modifier = AlphabetModifier(10)
    modifier.check(emptyArray()) { entry ->
      // Check all characters are letters
      val results = entry.toCharArray().map { it.isLetter() }
      false !in results
    }
  }
}

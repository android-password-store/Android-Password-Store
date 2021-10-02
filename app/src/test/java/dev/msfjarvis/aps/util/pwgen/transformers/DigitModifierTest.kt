/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import kotlin.test.Test

class DigitModifierTest {
  @Test
  fun `adds specified number of digits`() {
    val modifier = DigitModifier(10)
    modifier.check(emptyArray(), 10)
    modifier.check(arrayOf("Android", "Password", "Store"), 13)
  }

  @Test
  fun `all added entries are digits`() {
    val modifier = DigitModifier(10)
    modifier.check(emptyArray()) { entry ->
      // Check all characters are digits
      val results = entry.toCharArray().map { it.isDigit() }
      false !in results
    }
  }
}

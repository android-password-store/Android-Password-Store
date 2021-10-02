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

  private fun DigitModifier.check(input: Array<String>, expectedSize: Int) {
    assertEquals(expectedSize, transform(input).size)
  }
}

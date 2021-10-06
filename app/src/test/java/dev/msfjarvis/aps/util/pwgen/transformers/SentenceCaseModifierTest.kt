/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import kotlin.test.Test

class SentenceCaseModifierTest {

  private val modifier = SentenceCaseModifier()

  @Test
  fun `capitalizes an array of words`() {
    val input = arrayOf("android", "password", "store")
    modifier.check(input, expected = arrayOf("Android", "Password", "Store"))
  }

  @Test
  fun `array of numbers is not modified`() {
    val input = arrayOf("1", "2", "3")
    modifier.check(input, expected = input)
  }

  @Test
  fun `already capitalized items are not modified`() {
    val input = arrayOf("Android", "password", "Store")
    modifier.check(input, expected = arrayOf("Android", "Password", "Store"))
  }
}

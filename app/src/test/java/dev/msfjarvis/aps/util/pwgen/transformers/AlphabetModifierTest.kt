/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

  @Test
  fun `using both nouppercase and nolowercase in options throws`() {
    assertFailsWith<IllegalStateException> {
      AlphabetModifier(
        10,
        arrayOf(AlphabetModifier.Options.NoLowercase, AlphabetModifier.Options.NoUppercase),
      )
    }
  }

  @Test
  fun `NoUppercase option generates lowercase characters only`() {
    val modifier = AlphabetModifier(10, options = arrayOf(AlphabetModifier.Options.NoUppercase))
    val result = modifier.transform(emptyArray()).joinToString("")
    assertEquals(result.lowercase(), result)
  }

  @Test
  fun `NoLowercase option generates uppercase characters only`() {
    val modifier = AlphabetModifier(10, options = arrayOf(AlphabetModifier.Options.NoLowercase))
    val result = modifier.transform(emptyArray()).joinToString("")
    assertEquals(result.uppercase(), result)
  }
}

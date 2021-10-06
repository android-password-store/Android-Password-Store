/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen

import dev.msfjarvis.aps.util.pwgen.transformers.AllCapsModifier
import dev.msfjarvis.aps.util.pwgen.transformers.AlphabetModifier
import dev.msfjarvis.aps.util.pwgen.transformers.DigitModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransformingPasswordGeneratorTest {

  @Test
  fun `chaining uppercase modifier with alphabet modifier generates uppercase passwords`() {
    val modifiers = listOf(AlphabetModifier(count = 10), AllCapsModifier())
    val password = TransformingPasswordGenerator.generate(modifiers)
    assertEquals(password.uppercase(), password)
    assertNotEquals(password.lowercase(), password)
    assertEquals(10, password.length)
  }

  @Test
  fun `placing uppercase modifier before alphabet modifier does not generate uppercase password`() {
    val modifiers = listOf(AllCapsModifier(), AlphabetModifier(count = 10))
    val password = TransformingPasswordGenerator.generate(modifiers)
    assertNotEquals(password.uppercase(), password)
    assertEquals(10, password.length)
  }

  @Test
  fun `combining alphabet and digit modifier gives an alphanumeric password`() {
    val modifiers = listOf(AlphabetModifier(count = 10), DigitModifier(count = 10))
    val password = TransformingPasswordGenerator.generate(modifiers)
    val passwordChars = password.toCharArray()
    assertEquals(20, password.length)
    assertFalse(passwordChars.all { char -> char.isLetter() })
    assertFalse(passwordChars.all { char -> char.isDigit() })
    assertTrue(passwordChars.all { char -> char.isLetterOrDigit() })
  }
}

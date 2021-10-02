/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verify the output of the transformation of [input] is [expected]. */
fun PasswordModifier.check(input: Array<String>, expected: Array<String>) {
  assertContentEquals(expected, transform(input))
}

/** Verify the output of the transformation of [input] has [expectedSize] items. */
fun PasswordModifier.check(input: Array<String>, expectedSize: Int) {
  assertEquals(expectedSize, transform(input).size)
}

/** Transform [input] and ensure [checkEntry] is `true` for each item. */
fun PasswordModifier.check(input: Array<String>, checkEntry: (String) -> Boolean) {
  transform(input).forEach { entry -> assertTrue { checkEntry(entry) } }
}

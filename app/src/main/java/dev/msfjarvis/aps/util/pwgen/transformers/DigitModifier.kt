/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import dev.msfjarvis.aps.util.pwgen.secureRandomCharacter

/** Transforms the password tokens to add [count] distinct tokens of individual digits. */
class DigitModifier(private val count: Int) : PasswordModifier {

  override fun transform(input: Array<String>): Array<String> {
    if (count < 1) return input
    val items = input.toMutableList()
    repeat(count) { items.add(DIGITS.secureRandomCharacter().toString()) }
    return items.toTypedArray()
  }

  private companion object {
    private const val DIGITS = "0123456789"
  }
}

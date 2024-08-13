/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.passgen.random

public enum class PasswordOption(public val key: String) {
  NoDigits("0"),
  NoUppercaseLetters("A"),
  NoAmbiguousCharacters("B"),
  FullyRandom("s"),
  AtLeastOneSymbol("y"),
  NoLowercaseLetters("L"),
}

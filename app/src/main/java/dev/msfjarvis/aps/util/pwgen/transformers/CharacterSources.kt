/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

/** Character bank for [PasswordModifier] instances. */
object CharacterSources {
  const val DIGITS = "0123456789"
  const val UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  const val LOWERS_STR = "abcdefghijklmnopqrstuvwxyz"
  const val ALL_STR = "$UPPERS_STR$LOWERS_STR"
  const val SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
  const val AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2"
}

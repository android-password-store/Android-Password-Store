/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.passgen.random

import clearFlag
import dev.msfjarvis.aps.util.pwgen.RandomPhonemesGenerator
import hasFlag

public object PasswordGenerator {

  private const val DEFAULT_LENGTH = 16

  internal const val DIGITS = 0x0001
  internal const val UPPERS = 0x0002
  internal const val SYMBOLS = 0x0004
  internal const val NO_AMBIGUOUS = 0x0008
  internal const val LOWERS = 0x0020

  internal const val DIGITS_STR = "0123456789"
  internal const val UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  internal const val LOWERS_STR = "abcdefghijklmnopqrstuvwxyz"
  internal const val SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
  internal const val AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2"

  internal fun isValidPassword(password: String, pwFlags: Int): Boolean {
    if (pwFlags hasFlag DIGITS && password.none { it in DIGITS_STR }) return false
    if (pwFlags hasFlag UPPERS && password.none { it in UPPERS_STR }) return false
    if (pwFlags hasFlag LOWERS && password.none { it in LOWERS_STR }) return false
    if (pwFlags hasFlag SYMBOLS && password.none { it in SYMBOLS_STR }) return false
    if (pwFlags hasFlag NO_AMBIGUOUS && password.any { it in AMBIGUOUS_STR }) return false
    return true
  }

  /** Generates a password using the given [passwordOptions] and [length]. */
  @Throws(PasswordGeneratorException::class)
  public fun generate(passwordOptions: List<PasswordOption>, length: Int = DEFAULT_LENGTH): String {
    var numCharacterCategories = 0
    var phonemes = true
    var pwgenFlags = DIGITS or UPPERS or LOWERS

    for (option in PasswordOption.values()) {
      if (option in passwordOptions) {
        when (option) {
          PasswordOption.NoDigits -> pwgenFlags = pwgenFlags.clearFlag(DIGITS)
          PasswordOption.NoUppercaseLetters -> pwgenFlags = pwgenFlags.clearFlag(UPPERS)
          PasswordOption.NoLowercaseLetters -> pwgenFlags = pwgenFlags.clearFlag(LOWERS)
          PasswordOption.NoAmbiguousCharacters -> pwgenFlags = pwgenFlags or NO_AMBIGUOUS
          PasswordOption.FullyRandom -> phonemes = false
          PasswordOption.AtLeastOneSymbol -> {
            numCharacterCategories++
            pwgenFlags = pwgenFlags or SYMBOLS
          }
        }
      } else {
        // The No* options are false, so the respective character category will be included.
        when (option) {
          PasswordOption.NoDigits,
          PasswordOption.NoUppercaseLetters,
          PasswordOption.NoLowercaseLetters -> {
            numCharacterCategories++
          }
          PasswordOption.NoAmbiguousCharacters,
          PasswordOption.FullyRandom,
          // Since AtLeastOneSymbol is not negated, it is counted in the if branch.
          PasswordOption.AtLeastOneSymbol -> {}
        }
      }
    }

    if (pwgenFlags.clearFlag(NO_AMBIGUOUS) == 0) {
      throw NoCharactersIncludedException()
    }
    if (length < numCharacterCategories) { throw PasswordLengthTooShortException() }
    if (!(pwgenFlags hasFlag UPPERS) && !(pwgenFlags hasFlag LOWERS)) {
      phonemes = false
      pwgenFlags = pwgenFlags.clearFlag(NO_AMBIGUOUS)
    }
    // Experiments show that phonemes may require more than 1000 iterations to generate a valid
    // password if the length is not at least 6.
    if (length < 6) {
      phonemes = false
    }

    var password: String?
    var iterations = 0
    do {
      if (iterations++ > 1000)
        throw MaxIterationsExceededException()
      password =
        if (phonemes) {
          RandomPhonemesGenerator.generate(length, pwgenFlags)
        } else {
          RandomPasswordGenerator.generate(length, pwgenFlags)
        }
    } while (password == null)
    return password
  }
}

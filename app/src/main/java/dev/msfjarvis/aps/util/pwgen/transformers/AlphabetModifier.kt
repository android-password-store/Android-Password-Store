/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import dev.msfjarvis.aps.util.pwgen.secureRandomCharacter
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.ALL_STR
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.AMBIGUOUS_STR
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.LOWERS_STR
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.UPPERS_STR

/**
 * Adds [count] items from the alphabet, including both UPPERCASE and lowercase variants. Allows
 * customising what characters get added based on [options].
 */
class AlphabetModifier(
  private val count: Int,
  private val options: Array<Options> = emptyArray(),
) : PasswordModifier {

  init {
    check(!(options.contains(Options.NoUppercase) && options.contains(Options.NoLowercase))) {
      "Providing both NoUppercase and NoLowercase in options makes no sense"
    }
  }

  override fun transform(input: Array<String>): Array<String> {
    if (count < 1) return input
    val noAmbiguous = options.contains(Options.NoAmbiguous)
    val noUppercase = options.contains(Options.NoUppercase)
    val noLowercase = options.contains(Options.NoLowercase)
    val charBank =
      when {
        noUppercase -> LOWERS_STR
        noLowercase -> UPPERS_STR
        else -> ALL_STR
      }
    val items = input.toMutableList()
    repeat(count) {
      var char = charBank.secureRandomCharacter()
      while (noAmbiguous && char in AMBIGUOUS_STR) {
        char = charBank.secureRandomCharacter()
      }
      items.add(char.toString())
    }
    return items.toTypedArray()
  }

  enum class Options {
    NoAmbiguous,
    NoUppercase,
    NoLowercase,
  }
}

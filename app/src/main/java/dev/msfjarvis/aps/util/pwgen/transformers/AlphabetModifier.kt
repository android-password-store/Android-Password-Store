/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

import dev.msfjarvis.aps.util.pwgen.secureRandomCharacter
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.ALL_STR
import dev.msfjarvis.aps.util.pwgen.transformers.CharacterSources.AMBIGUOUS_STR

/**
 * Adds [count] items from the alphabet, including both UPPERCASE and lowercase variants. Allows
 * customising what characters get added based on [options].
 */
class AlphabetModifier(
  private val count: Int,
  private val options: Array<Options>,
) : PasswordModifier {

  override fun transform(input: Array<String>): Array<String> {
    if (count < 1) return input
    val noAmbiguous = options.contains(Options.NoAmbiguous)
    val items = input.toMutableList()
    repeat(count) {
      var char = ALL_STR.secureRandomCharacter()
      while (noAmbiguous && char in AMBIGUOUS_STR) {
        char = ALL_STR.secureRandomCharacter()
      }
      items.add(char.toString())
    }
    return items.toTypedArray()
  }

  enum class Options {
    NoAmbiguous,
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.passgen.diceware

import java.io.InputStream
import javax.inject.Inject

/**
 * Password generator implementing the Diceware passphrase generation mechanism. For detailed
 * information on how this works, see https://theworld.com/~reinhold/diceware.html.
 */
public class DicewarePassphraseGenerator
@Inject
constructor(
  private val die: Die,
  wordList: InputStream,
) {

  private val wordMap = WordListParser.parse(wordList)

  /** Generates a passphrase with [wordCount] words. */
  public fun generatePassphrase(wordCount: Int, separator: Char): String {
    return buildString {
      repeat(wordCount) { idx ->
        append(wordMap[die.rollMultiple(DIGITS)])
        if (idx < wordCount - 1) append(separator)
      }
    }
  }

  private companion object {

    /** Number of digits used by indices in the default wordlist. */
    const val DIGITS: Int = 5
  }
}

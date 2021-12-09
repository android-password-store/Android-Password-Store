/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.passgen.diceware

import javax.inject.Inject

/** Basic implementation of a die with configurable number of sides. */
public class Die
@Inject
constructor(
  private val sides: Int,
  private val random: RandomIntGenerator,
) {

  /** Roll the die to return a single number. */
  public fun roll(): Int {
    return random.get(1..sides)
  }

  /**
   * Roll the die multiple times, concatenating each result to obtain a number with [iterations]
   * digits.
   */
  public fun rollMultiple(iterations: Int): Int {
    return StringBuilder().apply { repeat(iterations) { append(roll()) } }.toString().toInt()
  }
}

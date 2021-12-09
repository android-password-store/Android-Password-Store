/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.passgen.diceware

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DieTest {

  /** Pre-seeded [Random] instance to ensure tests are deterministic. */
  private val random = Random(1_00_000)

  private val intGenerator = RandomIntGenerator { it.random(random) }

  @Test
  fun test_one_roll() {
    val die = Die(6, intGenerator)
    assertEquals(5, die.roll())
  }

  @Test
  fun test_multiple_rolls() {
    val die = Die(6, intGenerator)
    assertEquals(526242, die.rollMultiple(6))
  }

  @Test
  fun test_consecutive_rolls() {
    val die = Die(6, intGenerator)
    assertEquals(5, die.roll())
    assertEquals(2, die.roll())
    assertEquals(6, die.roll())
    assertEquals(2, die.roll())
    assertEquals(4, die.roll())
    assertEquals(2, die.roll())
  }

  @Test
  fun test_100_sides() {
    val die = Die(100, intGenerator)
    assertEquals(67, die.roll())
  }
}

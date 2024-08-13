/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.passgen.diceware

/**
 * SAM interface that takes in an [IntRange] and returns a randomly chosen [Int] within its bounds.
 * This is used as a replacement for [kotlin.random.Random] since there is no CSPRNG-backed
 * implementation of it in the Kotlin stdlib.
 */
public fun interface RandomIntGenerator {
  public fun get(range: IntRange): Int
}

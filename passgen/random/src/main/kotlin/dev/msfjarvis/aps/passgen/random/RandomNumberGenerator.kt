/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.passgen.random

import java.security.SecureRandom

private val secureRandom = SecureRandom()

/** Returns a number between 0 (inclusive) and [exclusiveBound](exclusive). */
internal fun secureRandomNumber(exclusiveBound: Int) = secureRandom.nextInt(exclusiveBound)

/** Returns `true` and `false` with probablity 50% each. */
internal fun secureRandomBoolean() = secureRandom.nextBoolean()

/**
 * Returns `true` with probability [percentTrue]% and `false` with probability `(100 - [percentTrue]
 * )`%.
 */
internal fun secureRandomBiasedBoolean(percentTrue: Int): Boolean {
  require(1 <= percentTrue) { "Probability for returning `true` must be at least 1%" }
  require(percentTrue <= 99) { "Probability for returning `true` must be at most 99%" }
  return secureRandomNumber(100) < percentTrue
}

internal fun <T> Array<T>.secureRandomElement() = this[secureRandomNumber(size)]

internal fun <T> List<T>.secureRandomElement() = this[secureRandomNumber(size)]

internal fun String.secureRandomCharacter() = this[secureRandomNumber(length)]

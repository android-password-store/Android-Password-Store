/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.passgen.random.util

/** Clears the given [flag] from the value of this [Int] */
internal infix fun Int.clearFlag(flag: Int): Int {
  return this and flag.inv()
}

/** Checks if this [Int] contains the given [flag] */
internal infix fun Int.hasFlag(flag: Int): Boolean {
  return this and flag == flag
}

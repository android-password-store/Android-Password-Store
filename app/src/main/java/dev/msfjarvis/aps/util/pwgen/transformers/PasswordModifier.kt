/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

/**
 * Defines a transformer that takes in an array of [String]s, performs a transformation and returns
 * another array of [String]s.
 */
fun interface PasswordModifier {

  /**
   * Performs the actual transformation of the [input] array and returning the result of the
   * transformation.
   */
  fun transform(input: Array<String>): Array<String>
}

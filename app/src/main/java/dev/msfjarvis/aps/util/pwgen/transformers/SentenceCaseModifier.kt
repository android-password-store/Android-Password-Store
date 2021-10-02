/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen.transformers

/** Converts the incoming text to sentence case. */
class SentenceCaseModifier : PasswordModifier {

  override fun transform(input: Array<String>): Array<String> {
    return input.map { string -> string.replaceFirstChar { it.uppercaseChar() } }.toTypedArray()
  }
}

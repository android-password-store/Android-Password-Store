/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.pwgen

import dev.msfjarvis.aps.util.pwgen.transformers.PasswordModifier

object TransformingPasswordGenerator {
  fun generate(modifiers: List<PasswordModifier>): String {
    var rootString = emptyArray<String>()
    modifiers.forEach { modifier -> rootString = modifier.transform(rootString) }
    return rootString.dropWhile { string -> string.isBlank() }.joinToString(separator = "")
  }
}

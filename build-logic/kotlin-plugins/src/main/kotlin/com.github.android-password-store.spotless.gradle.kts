/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { id("com.diffplug.spotless") }

val KTFMT_VERSION = "0.35"

spotless {
  kotlin {
    ktfmt(KTFMT_VERSION).googleStyle()
    target("**/*.kt")
    targetExclude("**/build/")
  }
  kotlinGradle {
    ktfmt(KTFMT_VERSION).googleStyle()
    target("**/*.kts")
    targetExclude("**/build/")
  }
  format("xml") {
    target("**/*.xml")
    targetExclude("**/build/", ".idea/")
    trimTrailingWhitespace()
    indentWithSpaces()
    endWithNewline()
  }
}

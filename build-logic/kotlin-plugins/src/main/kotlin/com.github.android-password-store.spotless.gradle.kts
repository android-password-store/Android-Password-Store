/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { id("com.diffplug.spotless") }

spotless {
  kotlin {
    ktfmt().googleStyle()
    target("**/*.kt")
    targetExclude("**/build/")
  }
  kotlinGradle {
    ktfmt().googleStyle()
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

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.diffplug.gradle.spotless.SpotlessExtension

internal fun SpotlessExtension.configureSpotless() {
  kotlin {
    ktfmt().googleStyle()
    target("src/**/*.kt", "**/*.kts")
  }
  format("xml") {
    target("**/*.xml")
    trimTrailingWhitespace()
    indentWithSpaces()
    endWithNewline()
  }
}

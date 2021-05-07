/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  `binary-compatibility-validator`
  `aps-plugin`
  // Fix for leak in Kotlin Gradle Plugin: https://youtrack.jetbrains.com/issue/KT-46368
  id("dev.zacsweers.kgp-150-leak-patcher") version "1.0.1"
}

allprojects { apply(plugin = "com.diffplug.spotless") }

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

plugins {
  id("com.github.android-password-store.kotlin-common")
  id("com.github.android-password-store.binary-compatibility")
  id("com.github.android-password-store.git-hooks")
  id("com.github.android-password-store.spotless")
  alias(libs.plugins.hilt) apply false
}

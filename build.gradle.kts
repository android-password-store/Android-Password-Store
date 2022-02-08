/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

buildscript {
  repositories { mavenCentral() }
  dependencies { classpath(libs.build.hilt) }
}

plugins {
  id("com.github.android-password-store.kotlin-common")
  id("com.github.android-password-store.binary-compatibility")
  id("com.github.android-password-store.git-hooks")
  id("com.github.android-password-store.spotless")
}

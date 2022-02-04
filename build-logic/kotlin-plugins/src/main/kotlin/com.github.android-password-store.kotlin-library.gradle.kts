/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { id("com.github.android-password-store.kotlin-common") }

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    if (!name.contains("test", ignoreCase = true)) {
      freeCompilerArgs += listOf("-Xexplicit-api=strict")
    }
  }
}

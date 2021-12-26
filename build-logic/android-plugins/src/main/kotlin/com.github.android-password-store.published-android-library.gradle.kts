/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.kotlin.dsl.provideDelegate

plugins {
  id("com.github.android-password-store.android-library")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

afterEvaluate {
  signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.plugins.signing.SigningExtension

plugins {
  id("com.github.android-password-store.android-library")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

afterEvaluate {
  extensions.configure<SigningExtension> {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}

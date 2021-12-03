/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.plugins.signing.SigningExtension

plugins {
  id("com.android.library")
  id("com.github.android-password-store.android-common")
}

afterEvaluate {
  extensions.configure<SigningExtension> {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}

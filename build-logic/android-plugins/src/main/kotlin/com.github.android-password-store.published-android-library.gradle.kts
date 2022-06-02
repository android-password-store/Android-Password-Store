/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.kotlin.dsl.provideDelegate

plugins {
  id("com.github.android-password-store.android-library")
  id("com.vanniktech.maven.publish.base")
  id("signing")
}

group = requireNotNull(project.findProperty("GROUP"))

version = requireNotNull(project.findProperty("VERSION_NAME"))

@Suppress("UnstableApiUsage")
mavenPublishing {
  publishToMavenCentral(SonatypeHost.DEFAULT)
  signAllPublications()
  configure(AndroidSingleVariantLibrary())
  pomFromGradleProperties()
}

afterEvaluate {
  signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}

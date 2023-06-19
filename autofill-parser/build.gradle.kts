/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.github.android-password-store.published-android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.psl-plugin")
}

android {
  defaultConfig {
    minSdk = 23
    consumerProguardFiles("consumer-rules.pro")
  }
  sourceSets { getByName("test") { resources.srcDir("src/main/assets") } }
  namespace = "com.github.androidpasswordstore.autofillparser"
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    languageVersion.set(KotlinVersion.KOTLIN_1_7)
  }
}

dependencies {
  implementation(platform(libs.kotlin1710.bom))
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.thirdparty.logcat)
  testImplementation(libs.bundles.testDependencies)
}

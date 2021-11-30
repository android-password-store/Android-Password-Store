/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { id("com.rickbusarow.gradle-dependency-sync") version "0.11.4" }

dependencies {
  val androidx_activity = "1.4.0"
  val androidx_test = "1.4.1-alpha03"
  val compose = "1.1.0-beta02"
  val composeSnapshot = "-"
  val coroutines = "1.5.2"
  val flowbinding = "1.2.0"
  val hilt = "2.40.1"
  val kotlin = "1.5.31"
  val lifecycle = "2.4.0"

  dependencySync("com.android.tools.build:gradle:7.0.3")
  dependencySync("org.jetbrains.kotlinx:binary-compatibility-validator:0.6.0")
  dependencySync("org.jetbrains.dokka:dokka-gradle-plugin:$kotlin")
  dependencySync("de.undercouch:gradle-download-task:4.1.2")
  dependencySync("com.google.dagger:hilt-android-gradle-plugin:$hilt")
  dependencySync("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
  dependencySync("com.vanniktech:gradle-maven-publish-plugin:0.18.0")
  dependencySync("com.squareup.okhttp3:okhttp:4.9.0")
  dependencySync("com.vdurmont:semver4j:3.1.0")
  dependencySync("com.diffplug.spotless:spotless-plugin-gradle:6.0.0")

  // Kotlin dependencies
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-core$coroutines")

  // AndroidX dependencies
  dependencySync("androidx.activity:activity-ktx:$androidx_activity")
  dependencySync("androidx.activity:activity-compose:$androidx_activity")
  dependencySync("androidx.annotation:annotation:1.3.0")
  dependencySync("androidx.autofill:autofill:1.2.0-beta01")
  dependencySync("androidx.appcompat:appcompat:1.4.0-rc01")
  dependencySync("androidx.biometric:biometric-ktx:1.2.0-alpha03")
  dependencySync("androidx.constraintlayout:constraintlayout:2.1.1")
  dependencySync("androidx.core:core-ktx:1.7.0")
  dependencySync("androidx.documentfile:documentfile:1.1.0-alpha01")
  dependencySync("androidx.fragment:fragment-ktx:1.4.0-rc01")
  dependencySync("androidx.hilt:hilt-navigation-compose:1.0.0-alpha03")
  dependencySync("androidx.lifecycle:lifecycle-common:$lifecycle")
  dependencySync("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle")
  dependencySync("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
  dependencySync("androidx.lifecycle:lifecycle-viewmodel-compose:1.0.0-alpha07")
  dependencySync("com.google.android.material:material:1.5.0-beta01")
  dependencySync("androidx.preference:preference:1.2.0-alpha02")
  dependencySync("androidx.recyclerview:recyclerview:1.3.0-alpha01")
  dependencySync("androidx.recyclerview:recyclerview-selection:1.2.0-alpha01")
  dependencySync("androidx.security:security-crypto:1.1.0-alpha03")
  dependencySync("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")

  // Compose dependencies
  dependencySync("androidx.compose.animation:animation:$compose")
  dependencySync("androidx.compose.compiler:compiler:$compose")
  dependencySync("androidx.compose.foundation:foundation:$compose")
  dependencySync("androidx.compose.foundation:foundation-layout:$compose")
  dependencySync("androidx.compose.material:material:$compose")
  dependencySync("androidx.compose.material3:material3:1.0.0-alpha01")
  dependencySync("androidx.compose.runtime:runtime:$compose")
  dependencySync("androidx.compose.ui:ui:$compose")
  dependencySync("androidx.compose.ui:ui-test-junit4:$compose")
  dependencySync("androidx.compose.ui:ui-tooling:$compose")
  dependencySync("androidx.compose.ui:ui-util:$compose")
  dependencySync("androidx.compose.ui:ui-viewbinding:$compose")

  // Dagger/Hilt dependencies
  dependencySync("com.google.dagger:hilt-android:$hilt")
  dependencySync("com.google.dagger:hilt-compiler:$hilt")
  dependencySync("com.google.dagger:hilt-core:$hilt")

  // Desugaring
  dependencySync("com.android.tools:desugar_jdk_libs:1.1.5")

  // First-party libraries
  dependencySync("com.github.android-password-store:sublime-fuzzy:1.0.0")
  dependencySync("com.github.android-password-store:zxing-android-embedded:4.2.1")

  // Third-party dependencies
  dependencySync("org.bouncycastle:bcprov-jdk15on:1.69")
  dependencySync("commons-codec:commons-codec:1.14")
  dependencySync("net.i2p.crypto:eddsa:0.3.0")
  dependencySync("me.zhanghai.android.fastscroll:library:1.1.7")
  dependencySync("io.github.reactivecircus.flowbinding:flowbinding-android:$flowbinding")
  dependencySync("org.eclipse.jgit:org.eclipse.jgit:3.7.1.201504261725-r")
  dependencySync("com.michael-bull.kotlin-result:kotlin-result:1.1.13")
  dependencySync("com.squareup.leakcanary:leakcanary-android:2.7")
  dependencySync("com.squareup.logcat:logcat:0.1")
  dependencySync("de.maxr1998:modernandroidpreferences:2.2.1")
  dependencySync("org.pgpainless:pgpainless-core:1.0.0-rc1")
  dependencySync("com.squareup.leakcanary:plumber-android:2.7")
  dependencySync("com.hierynomus:sshj:0.32.0")
  dependencySync("com.github.open-keychain.open-keychain:sshauthentication-api:5.7.5")
  dependencySync("com.github.haroldadmin:WhatTheStack:0.3.1")
  dependencySync("com.google.android.gms:play-services-auth-api-phone:17.5.1")

  // Testing dependencies
  dependencySync("junit:junit:4.13.2")
  dependencySync("org.jetbrains.kotlin:kotlin-test-junit")
  dependencySync("org.robolectric:robolectric:4.7")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  dependencySync("org.jetbrains.kotlin:kotlin-test-junit:1.5.31")
  dependencySync("com.github.android-password-store:shared-preferences-fake:2.0.0")
  dependencySync("androidx.test:rules:$androidx_test")
  dependencySync("androidx.test:runner:$androidx_test")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines")
}

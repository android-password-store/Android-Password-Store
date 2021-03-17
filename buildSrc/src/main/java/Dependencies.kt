/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

private const val KOTLIN_VERSION = "1.4.31"

object Plugins {
  const val androidGradlePlugin = "com.android.tools.build:gradle:4.1.2"
  const val binaryCompatibilityValidator =
      "org.jetbrains.kotlinx:binary-compatibility-validator:0.2.4"
  const val dokkaPlugin = "org.jetbrains.dokka:dokka-gradle-plugin:1.4.20"
  const val downloadTaskPlugin = "de.undercouch:gradle-download-task:4.1.1"
  const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION"
  const val ktfmtGradlePlugin = "com.ncorti.ktfmt.gradle:plugin:0.4.0"
  const val mavenPublishPlugin = "com.vanniktech:gradle-maven-publish-plugin:0.13.0"
  const val semver4j = "com.vdurmont:semver4j:3.1.0"
}

object Dependencies {
  object Kotlin {
    object Coroutines {

      private const val version = "1.4.2"
      const val android = "org.jetbrains.kotlinx:kotlinx-coroutines-android:$version"
      const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$version"
    }
  }

  object AndroidX {

    private const val lifecycleVersion = "2.3.0-rc01"

    const val activity_ktx = "androidx.activity:activity-ktx:1.3.0-alpha03"
    const val annotation = "androidx.annotation:annotation:1.1.0"
    const val autofill = "androidx.autofill:autofill:1.1.0"
    const val appcompat = "androidx.appcompat:appcompat:1.3.0-beta01"
    const val biometric_ktx = "androidx.biometric:biometric-ktx:1.2.0-alpha03"
    const val constraint_layout = "androidx.constraintlayout:constraintlayout:2.1.0-alpha2"
    const val core_ktx = "androidx.core:core-ktx:1.5.0-beta02"
    const val documentfile = "androidx.documentfile:documentfile:1.0.1"
    const val fragment_ktx = "androidx.fragment:fragment-ktx:1.3.0"
    const val lifecycle_common = "androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion"
    const val lifecycle_livedata_ktx = "androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion"
    const val lifecycle_viewmodel_ktx =
        "androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion"
    const val material = "com.google.android.material:material:1.3.0"
    const val preference = "androidx.preference:preference:1.1.1"
    const val recycler_view = "androidx.recyclerview:recyclerview:1.2.0-beta02"
    const val recycler_view_selection = "androidx.recyclerview:recyclerview-selection:1.1.0"
    const val security = "androidx.security:security-crypto:1.1.0-alpha03"
    const val swiperefreshlayout = "androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01"
  }

  object FirstParty {

    const val zxing_android_embedded =
        "com.github.android-password-store:zxing-android-embedded:4.1.0-aps"
  }

  object ThirdParty {

    const val bouncycastle = "org.bouncycastle:bcprov-jdk15on:1.67"
    const val commons_codec = "commons-codec:commons-codec:1.14"
    const val eddsa = "net.i2p.crypto:eddsa:0.3.0"
    const val fastscroll = "me.zhanghai.android.fastscroll:library:1.1.5"
    const val jgit = "org.eclipse.jgit:org.eclipse.jgit:3.7.1.201504261725-r"
    const val kotlin_result = "com.michael-bull.kotlin-result:kotlin-result:1.1.10"
    const val leakcanary = "com.squareup.leakcanary:leakcanary-android:2.6"
    const val modern_android_prefs = "de.maxr1998:modernandroidpreferences:2.0"
    const val plumber = "com.squareup.leakcanary:plumber-android:2.6"
    const val sshj = "com.hierynomus:sshj:0.31.0"
    const val ssh_auth = "com.github.open-keychain.open-keychain:sshauthentication-api:v5.7"
    const val timber = "com.jakewharton.timber:timber:4.7.1"
    const val timberkt = "com.github.ajalt:timberkt:1.5.1"
    const val whatthestack = "com.github.haroldadmin:WhatTheStack:0.3.0"
  }

  object NonFree {

    const val google_play_auth_api_phone =
        "com.google.android.gms:play-services-auth-api-phone:17.5.0"
  }

  object Testing {

    const val junit = "junit:junit:4.13.1"
    const val kotlin_test_junit = "org.jetbrains.kotlin:kotlin-test-junit:$KOTLIN_VERSION"

    object AndroidX {

      private const val version = "1.3.1-alpha03"
      const val runner = "androidx.test:runner:$version"
      const val rules = "androidx.test:rules:$version"
    }
  }
}

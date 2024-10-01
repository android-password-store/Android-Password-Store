/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("UnstableApiUsage")

plugins {
  id("com.github.android-password-store.android-application")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-kapt")
  id("com.github.android-password-store.versioning-plugin")
  id("com.github.android-password-store.sentry")
  id("com.github.android-password-store.rename-artifacts")
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.composeCompiler)
}

android {
  compileOptions { isCoreLibraryDesugaringEnabled = true }
  namespace = "app.passwordstore"

  defaultConfig {
    applicationId = "app.passwordstore"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { compose = true }

  androidResources { generateLocaleConfig = true }

  packaging { resources.excludes.add("META-INF/versions/**") }
}

dependencies {
  implementation(platform(libs.compose.bom))
  kapt(libs.dagger.hilt.compiler)
  implementation(libs.androidx.annotation)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
  implementation(projects.autofillParser)
  implementation(projects.coroutineUtils)
  implementation(projects.crypto.pgpainless)
  implementation(projects.format.common)
  implementation(projects.passgen.diceware)
  implementation(projects.passgen.random)
  implementation(projects.ui.compose)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometricKtx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.bundles.androidxLifecycle)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.recyclerviewSelection)
  implementation(libs.androidx.security)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.compose.ui.tooling)
  implementation(libs.dagger.hilt.android)

  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation(libs.aps.sublimeFuzzy)
  implementation(libs.aps.zxingAndroidEmbedded)

  implementation(libs.thirdparty.eddsa)
  implementation(libs.thirdparty.fastscroll)
  implementation(libs.thirdparty.flowbinding.android)
  implementation(libs.thirdparty.jgit) {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
  }
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.thirdparty.logcat)
  implementation(libs.thirdparty.modernAndroidPrefs)
  implementation(libs.thirdparty.leakcanary.plumber)
  implementation(libs.thirdparty.sshj)
  implementation(libs.thirdparty.bouncycastle.bcprov)
  implementation(libs.thirdparty.bouncycastle.bcutil)

  implementation(libs.thirdparty.slf4j.api) {
    because("SSHJ now uses SLF4J 2.0 which we don't want")
  }

  nonFreeImplementation(libs.thirdparty.nonfree.googlePlayAuthApiPhone)
  nonFreeImplementation(libs.thirdparty.nonfree.sentry)
  freeImplementation(projects.sentryStub)

  testImplementation(libs.testing.robolectric)
  testImplementation(libs.testing.sharedPrefsMock)
  testImplementation(libs.bundles.testDependencies)
}

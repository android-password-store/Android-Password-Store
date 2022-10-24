/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

plugins {
  id("com.github.android-password-store.android-application")
  id("com.github.android-password-store.crowdin-plugin")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-kapt")
  id("com.github.android-password-store.versioning-plugin")
  id("com.github.android-password-store.sentry")
  id("com.github.android-password-store.rename-artifacts")
  id("dagger.hilt.android.plugin")
}

crowdin {
  projectName = "android-password-store"
  skipCleanup = false
}

android {
  compileOptions { isCoreLibraryDesugaringEnabled = true }

  defaultConfig {
    applicationId = "app.passwordstore"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
    composeOptions {
      useLiveLiterals = false
      kotlinCompilerExtensionVersion = libs.compose.compiler.get().versionConstraint.requiredVersion
    }
  }

  namespace = "app.passwordstore"

  lint {
    abortOnError = true
    checkReleaseBuilds = false
    warningsAsErrors = true
    disable.add("MissingTranslation")
    disable.add("PluralsCandidate")
    disable.add("ImpliedQuantity")
    disable.add("DialogFragmentCallbacksDetector")
    baseline = file("lint-baseline.xml")
  }
}

dependencies {
  kapt(libs.dagger.hilt.compiler)
  implementation(libs.androidx.annotation)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
  implementation(projects.autofillParser)
  implementation(projects.coroutineUtils)
  implementation(projects.cryptoPgpainless)
  implementation(projects.formatCommonImpl)
  implementation(projects.passgen.diceware)
  implementation(projects.passgen.random)
  implementation(projects.uiCompose)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometricKtx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.bundles.androidxLifecycle)
  implementation(libs.androidx.material)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.recyclerviewSelection)
  implementation(libs.androidx.security)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.compose.ui.tooling)
  implementation(libs.dagger.hilt.android)

  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)

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
  implementation(libs.thirdparty.plumber)
  implementation(libs.thirdparty.sshj) { exclude(group = "org.bouncycastle") }
  implementation(libs.thirdparty.bouncycastle.bcprov)
  implementation(libs.thirdparty.bouncycastle.bcpkix)

  if (snapshot.snapshot) {
    implementation(libs.thirdparty.whatthestack)
  } else {
    debugImplementation(libs.thirdparty.whatthestack)
  }

  debugImplementation(libs.thirdparty.leakcanary)
  add("nonFreeImplementation", libs.thirdparty.nonfree.googlePlayAuthApiPhone)
  add("nonFreeImplementation", libs.thirdparty.nonfree.sentry)
  add("freeImplementation", projects.sentryStub)

  testImplementation(libs.testing.robolectric)
  testImplementation(libs.testing.sharedPrefsMock)
  testImplementation(libs.bundles.testDependencies)
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
  id("com.github.android-password-store.android-application")
  id("com.github.android-password-store.crowdin-plugin")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-kapt")
  id("com.github.android-password-store.versioning-plugin")
  id("com.github.android-password-store.sentry")
  id("com.github.android-password-store.rename-artifacts")
  id("com.github.android-password-store.bouncycastle-dependency")
  id("dagger.hilt.android.plugin")
}

crowdin {
  projectName = "android-password-store"
  skipCleanup = false
}

fun isSnapshot(): Boolean {
  with(project.providers) {
    val workflow = environmentVariable("GITHUB_WORKFLOW")
    val snapshot = environmentVariable("SNAPSHOT")
    return workflow.isPresent || snapshot.isPresent
  }
}

android {
  compileOptions { isCoreLibraryDesugaringEnabled = true }

  defaultConfig {
    applicationId = "dev.msfjarvis.aps"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  namespace = "dev.msfjarvis.aps"

  lint {
    abortOnError = true
    checkReleaseBuilds = false
    disable.add("MissingTranslation")
    disable.add("PluralsCandidate")
    disable.add("ImpliedQuantity")
  }
}

dependencies {
  kapt(libs.dagger.hilt.compiler)
  implementation(libs.androidx.annotation)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
  implementation(projects.autofillParser)
  implementation(projects.coroutineUtils)
  implementation(projects.cryptoPgpainless)
  implementation(projects.formatCommon)
  implementation(projects.openpgpKtx)
  implementation(projects.passgen.diceware)
  implementation(projects.passgen.random)
  implementation(libs.androidx.activity.ktx)
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
  implementation(libs.dagger.hilt.android)

  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)

  implementation(libs.aps.sublimeFuzzy)
  implementation(libs.aps.zxingAndroidEmbedded)

  implementation(libs.thirdparty.bouncycastle)
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
  implementation(libs.thirdparty.sshauth)
  implementation(libs.thirdparty.sshj)

  if (isSnapshot()) {
    implementation(libs.thirdparty.whatthestack)
  } else {
    debugImplementation(libs.thirdparty.whatthestack)
  }

  debugImplementation(libs.thirdparty.leakcanary)
  add("nonFreeImplementation", libs.thirdparty.nonfree.googlePlayAuthApiPhone)
  add("nonFreeImplementation", libs.thirdparty.nonfree.sentry)
  add("freeImplementation", projects.sentryStub)

  androidTestImplementation(libs.bundles.testDependencies)
  androidTestImplementation(libs.bundles.androidTestDependencies)
  testImplementation(libs.testing.robolectric)
  testImplementation(libs.testing.sharedPrefsMock)
  testImplementation(libs.bundles.testDependencies)
}

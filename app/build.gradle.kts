/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
  id("com.android.application")
  kotlin("android")
  kotlin("kapt")
  id("dagger.hilt.android.plugin")
  `versioning-plugin`
  `aps-plugin`
  `crowdin-plugin`
}

configure<CrowdinExtension> { projectName = "android-password-store" }

android {
  if (isSnapshot()) {
    applicationVariants.all {
      outputs.all {
        (this as BaseVariantOutputImpl).outputFileName = "aps-${flavorName}_$versionName.apk"
      }
    }
  }
  compileOptions { isCoreLibraryDesugaringEnabled = true }

  defaultConfig {
    applicationId = "dev.msfjarvis.aps"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  lintOptions {
    isAbortOnError = true
    isCheckReleaseBuilds = false
    disable("MissingTranslation", "PluralsCandidate", "ImpliedQuantity")
    // Kotlin 1.5 + AGP 4.1.3 trip up NewApi for Kotlin intrinsics like forEach
    // This can be fixed by either switching to AGP 4.2.0-rc1, or disabling it
    // outright.
    // https://issuetracker.google.com/issues/185418482
    disable("NewApi")
  }

  flavorDimensions("free")
  productFlavors {
    create("free") {}
    create("nonFree") {}
  }
}

dependencies {
  kapt(libs.dagger.hilt.compiler)
  implementation(libs.androidx.annotation)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
  implementation(projects.autofillParser)
  implementation(projects.formatCommon)
  implementation(projects.openpgpKtx)
  implementation(libs.androidx.activityKtx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometricKtx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.coreKtx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.fragmentKtx)
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
  implementation(libs.thirdparty.jgit) {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
  }
  implementation(libs.thirdparty.kotlinResult)
  implementation(libs.thirdparty.modernAndroidPrefs)
  implementation(libs.thirdparty.plumber)
  implementation(libs.thirdparty.sshauth)
  implementation(libs.thirdparty.sshj)
  implementation(libs.thirdparty.timber)
  implementation(libs.thirdparty.timberkt)

  if (isSnapshot()) {
    implementation(libs.thirdparty.leakcanary)
    implementation(libs.thirdparty.whatthestack)
  } else {
    debugImplementation(libs.thirdparty.leakcanary)
    debugImplementation(libs.thirdparty.whatthestack)
  }

  "nonFreeImplementation"(libs.thirdparty.nonfree.googlePlayAuthApiPhone)

  androidTestImplementation(libs.bundles.testDependencies)
  androidTestImplementation(libs.bundles.androidTestDependencies)
  testImplementation(libs.testing.robolectric)
  testImplementation(libs.bundles.testDependencies)
}

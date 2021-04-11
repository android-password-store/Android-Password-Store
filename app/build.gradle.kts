/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
  id("com.android.application")
  kotlin("android")
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

  defaultConfig {
    applicationId = "dev.msfjarvis.aps"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  lintOptions {
    isAbortOnError = true
    isCheckReleaseBuilds = false
    disable("MissingTranslation", "PluralsCandidate", "ImpliedQuantity")
  }

  flavorDimensions("free")
  productFlavors {
    create("free") {}
    create("nonFree") {}
  }
}

dependencies {
  compileOnly(libs.androidx.annotation)
  implementation(projects.autofillParser)
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

  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)

  implementation(libs.aps.sublimeFuzzy)
  implementation(libs.aps.zxingAndroidEmbedded)

  implementation(libs.thirdparty.bouncycastle)
  implementation(libs.thirdparty.commons.codec)
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
  testImplementation(libs.bundles.testDependencies)
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
  id("com.github.android-password-store.android-application")
  id("com.github.android-password-store.crowdin-plugin")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-kapt")
  id("com.github.android-password-store.versioning-plugin")
  id("dagger.hilt.android.plugin")
}

repositories {
  val composeSnapshot = libs.versions.composeSnapshot.get()
  if (composeSnapshot.length > 1) {
    maven("https://androidx.dev/snapshots/builds/$composeSnapshot/artifacts/repository/") {
      content {
        includeGroup("androidx.compose.animation")
        includeGroup("androidx.compose.compiler")
        includeGroup("androidx.compose.foundation")
        includeGroup("androidx.compose.material")
        includeGroup("androidx.compose.runtime")
        includeGroup("androidx.compose.ui")
      }
    }
  }
}

configure<crowdin.CrowdinExtension> {
  projectName = "android-password-store"
  skipCleanup = false
}

fun isSnapshot(): Boolean {
  with(project.providers) {
    val workflow = environmentVariable("GITHUB_WORKFLOW").forUseAtConfigurationTime()
    val snapshot = environmentVariable("SNAPSHOT").forUseAtConfigurationTime()
    return workflow.isPresent || snapshot.isPresent
  }
}

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

  buildFeatures.compose = true

  lint {
    isAbortOnError = true
    isCheckReleaseBuilds = false
    disable("MissingTranslation", "PluralsCandidate", "ImpliedQuantity")
    disable("CoroutineCreationDuringComposition")
  }

  composeOptions { kotlinCompilerExtensionVersion = libs.versions.compose.get() }
}

dependencies {
  kapt(libs.dagger.hilt.compiler)
  implementation(libs.androidx.annotation)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
  implementation(projects.autofillParser)
  implementation(projects.cryptoPgpainless)
  implementation(projects.formatCommon)
  implementation(projects.openpgpKtx)
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

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.hilt.compose)
  implementation(libs.compose.foundation.core)
  implementation(libs.compose.foundation.layout)
  implementation(libs.compose.material)
  implementation(libs.androidx.compose.material3)
  implementation(libs.compose.ui.core)
  implementation(libs.compose.ui.viewbinding)
  compileOnly(libs.compose.ui.tooling)

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

  androidTestImplementation(libs.bundles.testDependencies)
  androidTestImplementation(libs.bundles.androidTestDependencies)
  testImplementation(libs.testing.robolectric)
  testImplementation(libs.testing.sharedPrefsMock)
  testImplementation(libs.bundles.testDependencies)
}

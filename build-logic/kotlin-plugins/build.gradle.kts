/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { `kotlin-dsl` }

afterEvaluate {
  tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_11.toString()
      freeCompilerArgs = freeCompilerArgs + "-Xsam-conversions=class"
    }
  }
}

gradlePlugin {
  plugins {
    register("binary-compatibility") {
      id = "com.github.android-password-store.binary-compatibility"
      implementationClass = "dev.msfjarvis.aps.gradle.BinaryCompatibilityPlugin"
    }
    register("kotlin-android") {
      id = "com.github.android-password-store.kotlin-android"
      implementationClass = "dev.msfjarvis.aps.gradle.KotlinAndroidPlugin"
    }
    register("kotlin-common") {
      id = "com.github.android-password-store.kotlin-common"
      implementationClass = "dev.msfjarvis.aps.gradle.KotlinCommonPlugin"
    }
    register("kotlin-kapt") {
      id = "com.github.android-password-store.kotlin-kapt"
      implementationClass = "dev.msfjarvis.aps.gradle.KotlinKaptPlugin"
    }
    register("kotlin-library") {
      id = "com.github.android-password-store.kotlin-library"
      implementationClass = "dev.msfjarvis.aps.gradle.KotlinLibraryPlugin"
    }
    register("spotless") {
      id = "com.github.android-password-store.spotless"
      implementationClass = "dev.msfjarvis.aps.gradle.SpotlessPlugin"
    }
    register("versions") {
      id = "com.github.android-password-store.versions"
      implementationClass = "dev.msfjarvis.aps.gradle.DependencyUpdatesPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.binarycompat)
  implementation(libs.build.kotlin)
  implementation(libs.build.r8)
  implementation(libs.build.spotless)
  implementation(libs.build.vcu)
  implementation(libs.build.versions)
}

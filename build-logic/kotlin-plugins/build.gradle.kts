/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

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
      freeCompilerArgs = freeCompilerArgs + "-Xsam-conversions=class" + "-opt-in=kotlin.RequiresOptIn"
    }
  }
}

gradlePlugin {
  plugins {
    register("kotlin-android") {
      id = "com.github.android-password-store.kotlin-android"
      implementationClass = "app.passwordstore.gradle.KotlinAndroidPlugin"
    }
    register("kotlin-common") {
      id = "com.github.android-password-store.kotlin-common"
      implementationClass = "app.passwordstore.gradle.KotlinCommonPlugin"
    }
    register("kotlin-kapt") {
      id = "com.github.android-password-store.kotlin-kapt"
      implementationClass = "app.passwordstore.gradle.KotlinKaptPlugin"
    }
    register("kotlin-library") {
      id = "com.github.android-password-store.kotlin-library"
      implementationClass = "app.passwordstore.gradle.KotlinLibraryPlugin"
    }
    register("ktfmt") {
      id = "com.github.android-password-store.ktfmt"
      implementationClass = "app.passwordstore.gradle.KtfmtPlugin"
    }
    register("versions") {
      id = "com.github.android-password-store.versions"
      implementationClass = "app.passwordstore.gradle.DependencyUpdatesPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.detekt)
  implementation(libs.build.diffutils)
  implementation(libs.build.kotlin)
  implementation(libs.build.ktfmt)
  implementation(libs.build.r8)
  implementation(libs.build.vcu)
  implementation(libs.build.versions)
  implementation(libs.kotlin.coroutines.core)
}

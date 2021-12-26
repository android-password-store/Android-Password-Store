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
    kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() }
  }
}

gradlePlugin {
  plugins {
    register("versioning") {
      id = "com.github.android-password-store.versioning-plugin"
      implementationClass = "versioning.VersioningPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.dokka)
  implementation(libs.build.mavenpublish)
  implementation(libs.build.semver)
}

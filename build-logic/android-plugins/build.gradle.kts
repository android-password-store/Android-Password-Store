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
    register("versioning") {
      id = "com.github.android-password-store.versioning-plugin"
      implementationClass = "app.passwordstore.gradle.versioning.VersioningPlugin"
    }
    register("android-application") {
      id = "com.github.android-password-store.android-application"
      implementationClass = "app.passwordstore.gradle.ApplicationPlugin"
    }
    register("android-library") {
      id = "com.github.android-password-store.android-library"
      implementationClass = "app.passwordstore.gradle.LibraryPlugin"
    }
    register("published-android-library") {
      id = "com.github.android-password-store.published-android-library"
      implementationClass = "app.passwordstore.gradle.PublishedAndroidLibraryPlugin"
    }
    register("rename-artifacts") {
      id = "com.github.android-password-store.rename-artifacts"
      implementationClass = "app.passwordstore.gradle.RenameArtifactsPlugin"
    }
    register("sentry") {
      id = "com.github.android-password-store.sentry"
      implementationClass = "app.passwordstore.gradle.SentryPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.mavenpublish)
  implementation(libs.build.metalava)
  implementation(libs.build.r8)
  implementation(libs.build.semver)
  implementation(libs.build.sentry)
}

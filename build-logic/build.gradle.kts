/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { `kotlin-dsl` }

kotlin.jvmToolchain(17)

gradlePlugin {
  plugins {
    register("android-application") {
      id = "com.github.android-password-store.android-application"
      implementationClass = "app.passwordstore.gradle.ApplicationPlugin"
    }
    register("android-library") {
      id = "com.github.android-password-store.android-library"
      implementationClass = "app.passwordstore.gradle.LibraryPlugin"
    }
    register("git-hooks") {
      id = "com.github.android-password-store.git-hooks"
      implementationClass = "app.passwordstore.gradle.GitHooksPlugin"
    }
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
    register("kotlin-jvm-library") {
      id = "com.github.android-password-store.kotlin-jvm-library"
      implementationClass = "app.passwordstore.gradle.KotlinJVMLibrary"
    }
    register("published-android-library") {
      id = "com.github.android-password-store.published-android-library"
      implementationClass = "app.passwordstore.gradle.PublishedAndroidLibraryPlugin"
    }
    register("psl") {
      id = "com.github.android-password-store.psl-plugin"
      implementationClass = "app.passwordstore.gradle.psl.PublicSuffixListPlugin"
    }
    register("rename-artifacts") {
      id = "com.github.android-password-store.rename-artifacts"
      implementationClass = "app.passwordstore.gradle.RenameArtifactsPlugin"
    }
    register("sentry") {
      id = "com.github.android-password-store.sentry"
      implementationClass = "app.passwordstore.gradle.SentryPlugin"
    }
    register("spotless") {
      id = "com.github.android-password-store.spotless"
      implementationClass = "app.passwordstore.gradle.SpotlessPlugin"
    }
    register("versioning") {
      id = "com.github.android-password-store.versioning-plugin"
      implementationClass = "app.passwordstore.gradle.versioning.VersioningPlugin"
    }
    register("versions") {
      id = "com.github.android-password-store.versions"
      implementationClass = "app.passwordstore.gradle.DependencyUpdatesPlugin"
    }
  }
}

dependencies {
  implementation(platform(libs.kotlin.bom))
  implementation(libs.build.agp)
  implementation(libs.build.diffutils)
  implementation(libs.build.download)
  implementation(libs.build.javapoet)
  implementation(libs.build.kotlin)
  implementation(libs.build.mavenpublish)
  implementation(libs.build.metalava)
  implementation(libs.build.moshi)
  implementation(libs.build.moshi.kotlin)
  implementation(libs.build.okhttp)
  implementation(libs.build.r8)
  implementation(libs.build.semver)
  implementation(libs.build.sentry)
  implementation(libs.build.spotless)
  implementation(libs.build.vcu)
  implementation(libs.kotlinx.coroutines.core)

  // Expose the generated version catalog API to the plugin.
  implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}

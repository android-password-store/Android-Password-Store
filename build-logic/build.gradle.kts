/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { `kotlin-dsl` }

tasks.withType<JavaCompile>().configureEach {
  sourceCompatibility = JavaVersion.VERSION_11.toString()
  targetCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
    freeCompilerArgs = freeCompilerArgs + listOf(
      "-Xsam-conversions=class",
      "-opt-in=kotlin.RequiresOptIn",
    )
  }
}

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
    register("crowdin") {
      id = "com.github.android-password-store.crowdin-plugin"
      implementationClass = "app.passwordstore.gradle.crowdin.CrowdinDownloadPlugin"
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
    register("kotlin-library") {
      id = "com.github.android-password-store.kotlin-library"
      implementationClass = "app.passwordstore.gradle.KotlinLibraryPlugin"
    }
    register("ktfmt") {
      id = "com.github.android-password-store.ktfmt"
      implementationClass = "app.passwordstore.gradle.KtfmtPlugin"
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
  implementation(libs.build.detekt)
  implementation(libs.build.diffutils)
  implementation(libs.build.download)
  implementation(libs.build.kotlin)
  implementation(libs.build.ktfmt)
  implementation(libs.build.mavenpublish)
  implementation(libs.build.metalava)
  implementation(libs.build.okhttp)
  implementation(libs.build.r8)
  implementation(libs.build.semver)
  implementation(libs.build.sentry)
  implementation(libs.build.vcu)
  implementation(libs.build.versions)
  implementation(libs.kotlin.coroutines.core)
}

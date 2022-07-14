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
    register("crowdin") {
      id = "com.github.android-password-store.crowdin-plugin"
      implementationClass = "app.passwordstore.gradle.crowdin.CrowdinDownloadPlugin"
    }
    register("psl") {
      id = "com.github.android-password-store.psl-plugin"
      implementationClass = "app.passwordstore.gradle.psl.PublicSuffixListPlugin"
    }
    register("git-hooks") {
      id = "com.github.android-password-store.git-hooks"
      implementationClass = "app.passwordstore.gradle.GitHooksPlugin"
    }
  }
}

dependencies {
  implementation(libs.build.download)
  implementation(libs.build.okhttp)
}

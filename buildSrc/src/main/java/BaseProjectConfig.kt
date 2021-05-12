/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure root project. Note that classpath dependencies still need to be defined in the
 * `buildscript` block in the top-level build.gradle.kts file.
 */
internal fun Project.configureForRootProject() {
  tasks.withType<Wrapper> {
    gradleVersion = "7.0.1"
    distributionType = Wrapper.DistributionType.ALL
    distributionSha256Sum = "ca42877db3519b667cd531c414be517b294b0467059d401e7133f0e55b9bf265"
  }
  configureBinaryCompatibilityValidator()
}

/** Configure all projects including the root project */
internal fun Project.configureForAllProjects() {
  repositories {
    google()
    mavenCentral()
    @Suppress("DEPRECATION")
    jcenter {
      content {
        // https://github.com/zhanghai/AndroidFastScroll/issues/35
        includeModule("me.zhanghai.android.fastscroll", "library")
      }
    }
    maven("https://jitpack.io") {
      name = "JitPack"
      content {
        includeModule("com.github.haroldadmin", "WhatTheStack")
        includeModule("com.github.open-keychain.open-keychain", "sshauthentication-api")
      }
    }
  }
  tasks.withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
      freeCompilerArgs = freeCompilerArgs + additionalCompilerArgs
      languageVersion = "1.5"
    }
  }
  tasks.withType<Test> {
    maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
    testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
  }
}

/** Checks if we're building a snapshot */
@Suppress("UnstableApiUsage")
fun Project.isSnapshot(): Boolean {
  with(project.providers) {
    val workflow = environmentVariable("GITHUB_WORKFLOW").forUseAtConfigurationTime()
    val snapshot = environmentVariable("SNAPSHOT").forUseAtConfigurationTime()
    return workflow.isPresent && snapshot.isPresent
  }
}

/** Apply configurations for app module */
@Suppress("UnstableApiUsage")
internal fun BaseAppModuleExtension.configureAndroidApplicationOptions(project: Project) {
  val minifySwitch =
    project.providers.environmentVariable("DISABLE_MINIFY").forUseAtConfigurationTime()

  adbOptions.installOptions("--user 0")

  buildFeatures {
    viewBinding = true
    buildConfig = true
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = !minifySwitch.isPresent
      setProguardFiles(listOf("proguard-android-optimize.txt", "proguard-rules.pro"))
      buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "${project.isSnapshot()}")
    }
    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      isMinifyEnabled = false
      buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
    }
  }
}

/** Apply baseline configurations for all Android projects (Application and Library). */
@Suppress("UnstableApiUsage")
internal fun TestedExtension.configureCommonAndroidOptions() {
  compileSdkVersion(30)

  defaultConfig {
    minSdkVersion(23)
    targetSdkVersion(29)
  }

  sourceSets {
    named("main") { java.srcDirs("src/main/kotlin") }
    named("test") { java.srcDirs("src/test/kotlin") }
  }

  packagingOptions {
    exclude("**/*.version")
    exclude("**/*.txt")
    exclude("**/*.kotlin_module")
    exclude("**/plugin.properties")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  testOptions.animationsDisabled = true
}

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
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.nativeplatform.internal.BuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure root project. Note that classpath dependencies still need to be defined in the
 * `buildscript` block in the top-level build.gradle.kts file.
 */
internal fun Project.configureForRootProject() {
  tasks.withType<Wrapper> {
    gradleVersion = "7.3"
    distributionSha256Sum = "de8f52ad49bdc759164f72439a3bf56ddb1589c4cde802d3cec7d6ad0e0ee410"
  }
  configureBinaryCompatibilityValidator()
  tasks.register<GitHooks>("installGitHooks") {
    val projectDirectory = layout.projectDirectory
    hookScript.set(projectDirectory.file("scripts/pre-push-hook.sh").asFile.readText())
    hookOutput.set(projectDirectory.file(".git/hooks/pre-push").asFile)
  }
}

/** Configure all projects including the root project */
internal fun Project.configureForAllProjects() {
  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      allWarningsAsErrors = true
      jvmTarget = JavaVersion.VERSION_11.toString()
      freeCompilerArgs = freeCompilerArgs + additionalCompilerArgs
      languageVersion = "1.5"
    }
  }
  tasks.withType<Test>().configureEach {
    maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
    testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
    outputs.upToDateWhen { false }
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

  flavorDimensions.add(FlavorDimensions.FREE)
  productFlavors {
    register(ProductFlavors.FREE) {}
    register(ProductFlavors.NON_FREE) {}
  }

  buildTypes {
    named(BuildType.RELEASE.name) {
      isMinifyEnabled = !minifySwitch.isPresent
      setProguardFiles(
        listOf(
          "proguard-android-optimize.txt",
          "proguard-rules.pro",
          "proguard-rules-missing-classes.pro",
        )
      )
      buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "${project.isSnapshot()}")
    }
    named(BuildType.DEBUG.name) {
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
  setCompileSdkVersion(31)

  defaultConfig {
    minSdk = 23
    targetSdk = 29
  }

  sourceSets {
    named("main") { java.srcDirs("src/main/kotlin") }
    named("test") { java.srcDirs("src/test/kotlin") }
    named("androidTest") { java.srcDirs("src/androidTest/kotlin") }
  }

  packagingOptions {
    resources.excludes.add("**/*.version")
    resources.excludes.add("**/*.txt")
    resources.excludes.add("**/*.kotlin_module")
    resources.excludes.add("**/plugin.properties")
    resources.excludes.add("**/META-INF/AL2.0")
    resources.excludes.add("**/META-INF/LGPL2.1")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  testOptions {
    animationsDisabled = true
    unitTests.isReturnDefaultValues = true
  }
}

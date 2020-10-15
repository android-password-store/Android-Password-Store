/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.android.build.gradle.BaseExtension
import kotlinx.validation.ApiValidationExtension

buildscript {
    apply(from = "buildSrc/buildDependencies.gradle")
    val build: Map<Any, Any> by extra
    repositories {
        google()
        jcenter()
        // For binary compatibility validator.
        maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    }
    dependencies {
        classpath(build.getValue("androidGradlePlugin"))
        classpath(build.getValue("binaryCompatibilityValidator"))
        classpath(build.getValue("kotlinGradlePlugin"))
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.33.0"
}

apply(plugin = "binary-compatibility-validator")

extensions.configure<ApiValidationExtension> {
  ignoredProjects = mutableSetOf(
      "app"
  )
}

subprojects {
    repositories {
        google()
        jcenter()
        maven {
          setUrl("https://jitpack.io")
        }
    }
    if (name == "app") {
        apply(plugin = "com.android.application")
    } else {
        apply(plugin = "com.android.library")
    }
    configure<BaseExtension> {
        compileSdkVersion(29)
        defaultConfig {
            minSdkVersion(23)
            targetSdkVersion(29)
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        tasks.withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:unchecked")
            options.isDeprecation = true
        }
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn", "-Xallow-result-return-type")
            languageVersion = "1.4"
        }
    }
}

tasks.wrapper {
    gradleVersion = "6.7"
    distributionType = Wrapper.DistributionType.ALL
    distributionSha256Sum = "0080de8491f0918e4f529a6db6820fa0b9e818ee2386117f4394f95feb1d5583"
}

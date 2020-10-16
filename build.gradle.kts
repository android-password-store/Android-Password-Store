/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
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
    `binary-compatibility-validator`
    `aps-plugin`
}

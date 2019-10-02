/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://plugins.gradle.org/m2/")
    jcenter()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:3.24.3")
}

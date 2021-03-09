/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
    `binary-compatibility-validator`
    `aps-plugin`
}

buildscript {
    dependencies {
        classpath(Plugins.ktfmtGradlePlugin)
    }
}

allprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
}

subprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jetbrains.trove4j:trove4j:20160824"))
                .using(module("org.jetbrains.intellij.deps:trove4j:1.0.20200330"))
        }
    }
}

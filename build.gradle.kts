/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  `binary-compatibility-validator`
  `aps-plugin`
}

buildscript { dependencies { classpath(Plugins.ktfmtGradlePlugin) } }

allprojects { apply(plugin = "com.ncorti.ktfmt.gradle") }

subprojects {
  // Gradle's automatic resolution fails to handle AndroidX annotation for
  // some reason so here we simply hack it up to use the correct version manually.
  val annotationParts = Dependencies.AndroidX.annotation.split(":")
  val annotationGroup = annotationParts[0]
  val annotationModule = annotationParts[1]
  val annotationVersion = annotationParts[2]
  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("org.jetbrains.trove4j:trove4j:20160824"))
        .using(module("org.jetbrains.intellij.deps:trove4j:1.0.20200330"))
    }
    resolutionStrategy.eachDependency {
      if (requested.group == annotationGroup && requested.name == annotationModule) {
        useVersion(annotationVersion)
      }
    }
  }
}

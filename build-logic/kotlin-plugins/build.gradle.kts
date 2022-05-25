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

dependencies {
  implementation(libs.build.agp)
  implementation(libs.build.binarycompat)
  implementation(libs.build.kotlin)
  implementation(libs.build.spotless)
}

configurations.all {
  resolutionStrategy.eachDependency {
    val overrideName =
      "GRADLE_VERSION_OVERRIDE_${requested.group.replace('.', '_')}_${requested.name}"
    val overridenVersion = System.getenv(overrideName)
    if (!overridenVersion.isNullOrEmpty()) {
      project.logger.lifecycle(
        "Overriding dependency ${requested.group}:${requested.name} to version ${overridenVersion}"
      )
      useVersion(overridenVersion)
    }
  }
}

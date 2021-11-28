/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

rootProject.name = "build-logic"

enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
  versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

include("android-plugins")

include("automation-plugins")

include("kotlin-plugins")

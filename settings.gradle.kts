/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

// Modules
include(":app")

include(":autofill-parser")

include(":crypto-common")

include(":crypto-pgpainless")

include(":format-common")

include(":openpgp-ktx")

// Experimental features
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

enableFeaturePreview("VERSION_CATALOGS")

// Plugin repositories
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

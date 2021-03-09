/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureBinaryCompatibilityValidator() {
  extensions.configure<ApiValidationExtension> { ignoredProjects = mutableSetOf("app") }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import kotlinx.validation.ApiValidationExtension
import org.gradle.kotlin.dsl.configure

plugins { id("org.jetbrains.kotlinx.binary-compatibility-validator") }

extensions.configure<ApiValidationExtension> { ignoredProjects = mutableSetOf("app") }

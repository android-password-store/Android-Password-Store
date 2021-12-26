/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins { id("org.jetbrains.kotlinx.binary-compatibility-validator") }

apiValidation { ignoredProjects = mutableSetOf("app") }

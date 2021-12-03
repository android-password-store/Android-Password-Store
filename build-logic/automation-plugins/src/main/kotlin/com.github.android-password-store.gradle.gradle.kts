/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.withType

tasks.withType<Wrapper> {
  gradleVersion = "7.3.1"
  distributionSha256Sum = "9afb3ca688fc12c761a0e9e4321e4d24e977a4a8916c8a768b1fe05ddb4d6b66"
}

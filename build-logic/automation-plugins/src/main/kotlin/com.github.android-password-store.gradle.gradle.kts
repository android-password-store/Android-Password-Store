/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.withType

tasks.withType<Wrapper> {
  gradleVersion = "7.3.3"
  distributionSha256Sum = "b586e04868a22fd817c8971330fec37e298f3242eb85c374181b12d637f80302"
}

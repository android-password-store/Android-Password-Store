/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.withType

tasks.withType<Wrapper> {
  gradleVersion = "7.4-rc-2"
  distributionSha256Sum = "21491c9f0656e1529ccb39cbd587d01c33ba00d25f994b10240748ed0d45894a"
}

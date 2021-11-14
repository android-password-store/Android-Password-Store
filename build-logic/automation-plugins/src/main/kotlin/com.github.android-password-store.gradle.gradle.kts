/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.withType

tasks.withType<Wrapper> {
  gradleVersion = "7.3"
  distributionSha256Sum = "de8f52ad49bdc759164f72439a3bf56ddb1589c4cde802d3cec7d6ad0e0ee410"
}

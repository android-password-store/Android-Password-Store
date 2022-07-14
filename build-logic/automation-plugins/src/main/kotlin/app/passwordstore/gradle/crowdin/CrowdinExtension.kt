/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.crowdin

/** Extension for configuring [CrowdinDownloadPlugin] */
interface CrowdinExtension {

  /** Configure the project name on Crowdin */
  var projectName: String

  /**
   * Don't delete downloaded and extracted translation archives from build directory.
   *
   * Useful for debugging.
   */
  var skipCleanup: Boolean
}

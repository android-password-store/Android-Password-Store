/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package crowdin

/** Extension for configuring [CrowdinPlugin] */
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

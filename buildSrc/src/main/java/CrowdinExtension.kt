/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

open class CrowdinExtension {

    /**
     * Configure the project name on Crowdin
     */
    open var projectName = ""

    /**
     * Don't delete downloaded and extracted translation archives from build directory.
     *
     * Useful for debugging.
     */
    open var skipCleanup = false
}

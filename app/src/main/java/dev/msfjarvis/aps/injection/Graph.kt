/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection

import dev.msfjarvis.aps.util.repo.FileStoreImpl
import dev.msfjarvis.aps.util.repo.FileXStoreImpl
import dev.msfjarvis.aps.util.repo.Store
import java.io.File

/**
 * Extremely rudimentary dependency injection container
 */
object Graph {

    lateinit var store: Store

    fun buildStoreImpl(baseDir: File, external: Boolean) {
        store = if (external)
            FileXStoreImpl(baseDir)
        else
            FileStoreImpl(baseDir)
    }
}

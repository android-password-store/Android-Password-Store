/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection

import android.content.Context
import dev.msfjarvis.aps.util.repo.FileStoreImpl
import dev.msfjarvis.aps.util.repo.FileXStoreImpl
import dev.msfjarvis.aps.util.repo.Store
import java.io.File

/**
 * Extremely rudimentary dependency injection container
 */
object Graph {

    lateinit var store: Store

    fun buildStoreImpl(context: Context, baseDir: File) {
        val external = !baseDir.absolutePath.startsWith("${context.filesDir}")
        store = if (external)
            FileXStoreImpl(baseDir)
        else
            FileStoreImpl(baseDir)
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection

import android.content.Context
import androidx.core.net.toUri
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.repo.FileStoreImpl
import dev.msfjarvis.aps.util.repo.FileXStoreImpl
import dev.msfjarvis.aps.util.repo.Store
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.File

/**
 * Extremely rudimentary dependency injection container
 */
object Graph {

    lateinit var store: Store

    fun buildStoreImpl(context: Context) {
        val settings = context.sharedPrefs
        val externalRepo = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
        store = if (externalRepo != null)
            FileXStoreImpl(externalRepo.toUri())
        else
            FileStoreImpl(File("${context.filesDir}/store"))
    }
}

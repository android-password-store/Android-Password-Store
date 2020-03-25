/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import java.io.File
import java.nio.file.Paths

private val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this)

enum class DirectoryStructure(val value: String) {
    FileBased("file"),
    DirectoryBased("directory");

    fun getUsernameFor(file: File) = when (this) {
        FileBased -> file.nameWithoutExtension
        DirectoryBased -> file.parentFile?.name ?: file.nameWithoutExtension
    }

    fun getIdentifierFor(file: File) = when (this) {
        FileBased -> file.parentFile?.name
        DirectoryBased -> file.parentFile?.parentFile?.name
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getSaveFolderName(sanitizedIdentifier: String, username: String?) = when (this) {
        FileBased -> sanitizedIdentifier
        DirectoryBased -> Paths.get(sanitizedIdentifier, username ?: "username").toString()
    }

    fun getSaveFileName(username: String?) = when (this) {
        FileBased -> username
        DirectoryBased -> "password"
    }

    companion object {
        const val PREFERENCE = "oreo_autofill_directory_structure"
        private val DEFAULT = FileBased

        private val reverseMap = values().associateBy { it.value }
        fun fromValue(value: String?) = if (value != null) reverseMap[value] ?: DEFAULT else DEFAULT
    }
}

object AutofillPreferences {

    fun directoryStructure(context: Context): DirectoryStructure {
        val value =
            context.defaultSharedPreferences.getString(DirectoryStructure.PREFERENCE, null)
        return DirectoryStructure.fromValue(value)
    }
}

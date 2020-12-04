/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.util.autofill

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.github.androidpasswordstore.autofillparser.Credentials
import com.zeapo.pwdstore.data.password.PasswordEntry
import com.zeapo.pwdstore.util.extensions.sharedPrefs
import com.zeapo.pwdstore.util.services.getDefaultUsername
import java.io.File
import java.nio.file.Paths

enum class DirectoryStructure(val value: String) {
    EncryptedUsername("encrypted_username"),
    FileBased("file"),
    DirectoryBased("directory");

    /**
     * Returns the username associated to [file], following the convention of the current
     * [DirectoryStructure].
     *
     * Examples:
     *   - * --> null (EncryptedUsername)
     *   - work/example.org/john@doe.org.gpg --> john@doe.org (FileBased)
     *   - work/example.org/john@doe.org/password.gpg --> john@doe.org (DirectoryBased)
     *   - Temporary PIN.gpg --> Temporary PIN (DirectoryBased, fallback)
     */
    fun getUsernameFor(file: File): String? = when (this) {
        EncryptedUsername -> null
        FileBased -> file.nameWithoutExtension
        DirectoryBased -> file.parentFile?.name ?: file.nameWithoutExtension
    }

    /**
     * Returns the origin identifier associated to [file], following the convention of the current
     * [DirectoryStructure].
     *
     * At least one of [DirectoryStructure.getIdentifierFor] and
     * [DirectoryStructure.getAccountPartFor] will always return a non-null result.
     *
     * Examples:
     *   - work/example.org.gpg --> example.org (EncryptedUsername)
     *   - work/example.org/john@doe.org.gpg --> example.org (FileBased)
     *   - example.org.gpg --> example.org (FileBased, fallback)
     *   - work/example.org/john@doe.org/password.gpg --> example.org (DirectoryBased)
     *   - Temporary PIN.gpg --> null (DirectoryBased)
     */
    fun getIdentifierFor(file: File): String? = when (this) {
        EncryptedUsername -> file.nameWithoutExtension
        FileBased -> file.parentFile?.name ?: file.nameWithoutExtension
        DirectoryBased -> file.parentFile?.parent
    }

    /**
     * Returns the path components of [file] until right before the component that contains the
     * origin identifier according to the current [DirectoryStructure].
     *
     * Examples:
     *   - work/example.org.gpg --> work (EncryptedUsername)
     *   - work/example.org/john@doe.org.gpg --> work (FileBased)
     *   - example.org/john@doe.org.gpg --> null (FileBased)
     *   - john@doe.org.gpg --> null (FileBased)
     *   - work/example.org/john@doe.org/password.gpg --> work (DirectoryBased)
     *   - example.org/john@doe.org/password.gpg --> null (DirectoryBased)
     */
    fun getPathToIdentifierFor(file: File): String? = when (this) {
        EncryptedUsername -> file.parent
        FileBased -> file.parentFile?.parent
        DirectoryBased -> file.parentFile?.parentFile?.parent
    }

    /**
     * Returns the path component of [file] following the origin identifier according to the current
     * [DirectoryStructure] (without file extension).
     *
     * At least one of [DirectoryStructure.getIdentifierFor] and
     * [DirectoryStructure.getAccountPartFor] will always return a non-null result.
     *
     * Examples:
     *   - * --> null (EncryptedUsername)
     *   - work/example.org/john@doe.org.gpg --> john@doe.org (FileBased)
     *   - example.org.gpg --> null (FileBased, fallback)
     *   - work/example.org/john@doe.org/password.gpg --> john@doe.org/password (DirectoryBased)
     *   - Temporary PIN.gpg --> Temporary PIN (DirectoryBased, fallback)
     */
    fun getAccountPartFor(file: File): String? = when (this) {
        EncryptedUsername -> null
        FileBased -> file.nameWithoutExtension.takeIf { file.parentFile != null }
        DirectoryBased -> file.parentFile?.let { parentFile ->
            "${parentFile.name}/${file.nameWithoutExtension}"
        } ?: file.nameWithoutExtension
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getSaveFolderName(sanitizedIdentifier: String, username: String?) = when (this) {
        EncryptedUsername -> "/"
        FileBased -> sanitizedIdentifier
        DirectoryBased -> Paths.get(sanitizedIdentifier, username ?: "username").toString()
    }

    fun getSaveFileName(username: String?, identifier: String) = when (this) {
        EncryptedUsername -> identifier
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
        val value = context.sharedPrefs.getString(DirectoryStructure.PREFERENCE, null)
        return DirectoryStructure.fromValue(value)
    }

    fun credentialsFromStoreEntry(
        context: Context,
        file: File,
        entry: PasswordEntry,
        directoryStructure: DirectoryStructure
    ): Credentials {
        // Always give priority to a username stored in the encrypted extras
        val username = entry.username
            ?: directoryStructure.getUsernameFor(file)
            ?: context.getDefaultUsername()
        return Credentials(username, entry.password, entry.calculateTotpCode())
    }
}

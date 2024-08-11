/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.autofill

import android.content.Context
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.services.getDefaultUsername
import app.passwordstore.util.settings.PreferenceKeys
import com.github.androidpasswordstore.autofillparser.Credentials
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

enum class DirectoryStructure(val value: String) {
  EncryptedUsername("encrypted_username"),
  FileBased("file"),
  DirectoryBased("directory");

  /**
   * Returns the username associated to [file], following the convention of the current
   * [DirectoryStructure].
   *
   * Examples:
   * - * --> null (EncryptedUsername)
   * - work/example.org/john@doe.org.gpg --> john@doe.org (FileBased)
   * - work/example.org/john@doe.org/password.gpg --> john@doe.org (DirectoryBased)
   * - Temporary PIN.gpg --> Temporary PIN (DirectoryBased, fallback)
   */
  fun getUsernameFor(file: Path): String? =
    when (this) {
      EncryptedUsername -> null
      FileBased -> file.nameWithoutExtension
      DirectoryBased -> file.parent?.name ?: file.nameWithoutExtension
    }

  /**
   * Returns the origin identifier associated to [file], following the convention of the current
   * [DirectoryStructure].
   *
   * At least one of [DirectoryStructure.getIdentifierFor] and
   * [DirectoryStructure.getAccountPartFor] will always return a non-null result.
   *
   * Examples:
   * - work/example.org.gpg --> example.org (EncryptedUsername)
   * - work/example.org/john@doe.org.gpg --> example.org (FileBased)
   * - example.org.gpg --> example.org (FileBased, fallback)
   * - work/example.org/john@doe.org/password.gpg --> example.org (DirectoryBased)
   * - Temporary PIN.gpg --> null (DirectoryBased)
   */
  fun getIdentifierFor(file: Path): String? =
    when (this) {
      EncryptedUsername -> file.nameWithoutExtension
      FileBased -> file.parent?.name ?: file.nameWithoutExtension
      DirectoryBased -> file.parent?.parent?.pathString
    }

  /**
   * Returns the path components of [file] until right before the component that contains the origin
   * identifier according to the current [DirectoryStructure].
   *
   * Examples:
   * - work/example.org.gpg --> work (EncryptedUsername)
   * - work/example.org/john@doe.org.gpg --> work (FileBased)
   * - example.org/john@doe.org.gpg --> null (FileBased)
   * - john@doe.org.gpg --> null (FileBased)
   * - work/example.org/john@doe.org/password.gpg --> work (DirectoryBased)
   * - example.org/john@doe.org/password.gpg --> null (DirectoryBased)
   */
  fun getPathToIdentifierFor(file: Path): String? =
    when (this) {
      EncryptedUsername -> file.parent.pathString
      FileBased -> file.parent?.parent?.pathString
      DirectoryBased -> file.parent?.parent?.parent?.pathString
    }

  /**
   * Returns the path component of [file] following the origin identifier according to the current
   * [DirectoryStructure](without file extension).
   *
   * At least one of [DirectoryStructure.getIdentifierFor] and
   * [DirectoryStructure.getAccountPartFor] will always return a non-null result.
   *
   * Examples:
   * - * --> null (EncryptedUsername)
   * - work/example.org/john@doe.org.gpg --> john@doe.org (FileBased)
   * - example.org.gpg --> null (FileBased, fallback)
   * - work/example.org/john@doe.org/password.gpg --> john@doe.org/password (DirectoryBased)
   * - Temporary PIN.gpg --> Temporary PIN (DirectoryBased, fallback)
   */
  fun getAccountPartFor(file: Path): String? =
    when (this) {
      EncryptedUsername -> null
      FileBased -> file.nameWithoutExtension.takeIf { file.parent != null }
      DirectoryBased ->
        file.parent?.let { parent -> "${parent.name}/${file.nameWithoutExtension}" }
          ?: file.nameWithoutExtension
    }

  fun getSaveFolderName(sanitizedIdentifier: String, username: String?) =
    when (this) {
      EncryptedUsername -> "/"
      FileBased -> sanitizedIdentifier
      DirectoryBased -> Paths.get(sanitizedIdentifier, username ?: "username").toString()
    }

  fun getSaveFileName(username: String?, identifier: String) =
    when (this) {
      EncryptedUsername -> identifier
      FileBased -> username
      DirectoryBased -> "password"
    }

  companion object {

    val DEFAULT = FileBased

    private val reverseMap = entries.associateBy { it.value }

    fun fromValue(value: String?) = if (value != null) reverseMap[value] ?: DEFAULT else DEFAULT
  }
}

object AutofillPreferences {

  fun directoryStructure(context: Context): DirectoryStructure {
    val value = context.sharedPrefs.getString(PreferenceKeys.OREO_AUTOFILL_DIRECTORY_STRUCTURE)
    return DirectoryStructure.fromValue(value)
  }

  fun credentialsFromStoreEntry(
    context: Context,
    path: Path,
    entry: PasswordEntry,
    directoryStructure: DirectoryStructure,
  ): Credentials {
    // Always give priority to a username stored in the encrypted extras
    val username =
      entry.username ?: directoryStructure.getUsernameFor(path) ?: context.getDefaultUsername()
    val totp = if (entry.hasTotp()) entry.currentOtp else null
    return Credentials(username, entry.password, totp)
  }
}

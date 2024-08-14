/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.autofill

import android.content.Context
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.services.getDefaultUsername
import app.passwordstore.util.settings.DirectoryStructure
import app.passwordstore.util.settings.PreferenceKeys
import com.github.androidpasswordstore.autofillparser.Credentials
import java.io.File

object AutofillPreferences {

  fun directoryStructure(context: Context): DirectoryStructure {
    val value = context.sharedPrefs.getString(PreferenceKeys.DIRECTORY_STRUCTURE)
    return DirectoryStructure.fromValue(value)
  }

  fun credentialsFromStoreEntry(
    context: Context,
    file: File,
    entry: PasswordEntry,
    directoryStructure: DirectoryStructure,
  ): Credentials {
    // Always give priority to a username stored in the encrypted extras
    val username =
      entry.username ?: directoryStructure.getUsernameFor(file) ?: context.getDefaultUsername()
    val totp = if (entry.hasTotp()) entry.currentOtp else null
    return Credentials(username, entry.password, totp)
  }
}

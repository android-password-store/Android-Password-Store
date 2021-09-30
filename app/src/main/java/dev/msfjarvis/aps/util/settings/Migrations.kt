/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DEPRECATION")

package dev.msfjarvis.aps.util.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.github.michaelbull.result.get
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.git.sshj.SshKey
import java.io.File
import java.net.URI

fun runMigrations(filesDirPath: String, sharedPrefs: SharedPreferences, gitSettings: GitSettings) {
  migrateToGitUrlBasedConfig(sharedPrefs, gitSettings)
  migrateToHideAll(sharedPrefs)
  migrateToSshKey(filesDirPath, sharedPrefs)
  migrateToClipboardHistory(sharedPrefs)
}

private fun migrateToGitUrlBasedConfig(sharedPrefs: SharedPreferences, gitSettings: GitSettings) {
  val serverHostname = sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_SERVER) ?: return
  i { "Migrating to URL-based Git config" }
  val serverPort = sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_PORT) ?: ""
  val serverUser = sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_USERNAME) ?: ""
  val serverPath = sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_LOCATION) ?: ""
  val protocol = Protocol.fromString(sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_PROTOCOL))

  // Whether we need the leading ssh:// depends on the use of a custom port.
  val hostnamePart = serverHostname.removePrefix("ssh://")
  val url =
    when (protocol) {
      Protocol.Ssh -> {
        val userPart = if (serverUser.isEmpty()) "" else "${serverUser.trimEnd('@')}@"
        val portPart = if (serverPort == "22" || serverPort.isEmpty()) "" else ":$serverPort"
        if (portPart.isEmpty()) {
          "$userPart$hostnamePart:$serverPath"
        } else {
          // Only absolute paths are supported with custom ports.
          if (!serverPath.startsWith('/')) null
          else
          // We have to specify the ssh scheme as this is the only way to pass a custom
          // port.
          "ssh://$userPart$hostnamePart$portPart$serverPath"
        }
      }
      Protocol.Https -> {
        val portPart = if (serverPort == "443" || serverPort.isEmpty()) "" else ":$serverPort"
        val pathPart = serverPath.trimStart('/', ':')
        val urlWithFreeEntryScheme = "$hostnamePart$portPart/$pathPart"
        val url =
          when {
            urlWithFreeEntryScheme.startsWith("https://") -> urlWithFreeEntryScheme
            urlWithFreeEntryScheme.startsWith("http://") ->
              urlWithFreeEntryScheme.replaceFirst("http", "https")
            else -> "https://$urlWithFreeEntryScheme"
          }
        runCatching { if (URI(url).rawAuthority != null) url else null }.get()
      }
    }

  sharedPrefs.edit {
    remove(PreferenceKeys.GIT_REMOTE_LOCATION)
    remove(PreferenceKeys.GIT_REMOTE_PORT)
    remove(PreferenceKeys.GIT_REMOTE_SERVER)
    remove(PreferenceKeys.GIT_REMOTE_USERNAME)
    remove(PreferenceKeys.GIT_REMOTE_PROTOCOL)
  }
  if (url == null ||
      gitSettings.updateConnectionSettingsIfValid(
        newAuthMode = gitSettings.authMode,
        newUrl = url,
        newBranch = gitSettings.branch
      ) != GitSettings.UpdateConnectionSettingsResult.Valid
  ) {
    e { "Failed to migrate to URL-based Git config, generated URL is invalid" }
  }
}

private fun migrateToHideAll(sharedPrefs: SharedPreferences) {
  sharedPrefs.all[PreferenceKeys.SHOW_HIDDEN_FOLDERS] ?: return
  val isHidden = sharedPrefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_FOLDERS, false)
  sharedPrefs.edit {
    remove(PreferenceKeys.SHOW_HIDDEN_FOLDERS)
    putBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, isHidden)
  }
}

private fun migrateToSshKey(filesDirPath: String, sharedPrefs: SharedPreferences) {
  val privateKeyFile = File(filesDirPath, ".ssh_key")
  if (sharedPrefs.contains(PreferenceKeys.USE_GENERATED_KEY) &&
      !SshKey.exists &&
      privateKeyFile.exists()
  ) {
    // Currently uses a private key imported or generated with an old version of Password Store.
    // Generated keys come with a public key which the user should still be able to view after
    // the migration (not possible for regular imported keys), hence the special case.
    val isGeneratedKey = sharedPrefs.getBoolean(PreferenceKeys.USE_GENERATED_KEY, false)
    SshKey.useLegacyKey(isGeneratedKey)
    sharedPrefs.edit { remove(PreferenceKeys.USE_GENERATED_KEY) }
  }
}

private fun migrateToClipboardHistory(sharedPrefs: SharedPreferences) {
  if (sharedPrefs.contains(PreferenceKeys.CLEAR_CLIPBOARD_20X)) {
    sharedPrefs.edit {
      putBoolean(
        PreferenceKeys.CLEAR_CLIPBOARD_HISTORY,
        sharedPrefs.getBoolean(PreferenceKeys.CLEAR_CLIPBOARD_20X, false)
      )
      remove(PreferenceKeys.CLEAR_CLIPBOARD_20X)
    }
  }
}

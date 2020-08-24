/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DEPRECATION")

package com.zeapo.pwdstore

import android.content.Context
import androidx.core.content.edit
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs
import java.net.URI

fun runMigrations(context: Context) {
    migrateToGitUrlBasedConfig(context)
}

private fun migrateToGitUrlBasedConfig(context: Context) {
    val serverHostname = context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_SERVER)
        ?: return
    i { "Migrating to URL-based Git config" }
    val serverPort = context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_PORT) ?: ""
    val serverUser = context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_USERNAME) ?: ""
    val serverPath = context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_LOCATION) ?: ""
    val protocol = Protocol.fromString(context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_PROTOCOL))

    // Whether we need the leading ssh:// depends on the use of a custom port.
    val hostnamePart = serverHostname.removePrefix("ssh://")
    val url = when (protocol) {
        Protocol.Ssh -> {
            val userPart = if (serverUser.isEmpty()) "" else "${serverUser.trimEnd('@')}@"
            val portPart =
                if (serverPort == "22" || serverPort.isEmpty()) "" else ":$serverPort"
            if (portPart.isEmpty()) {
                "$userPart$hostnamePart:$serverPath"
            } else {
                // Only absolute paths are supported with custom ports.
                if (!serverPath.startsWith('/'))
                    null
                else
                    // We have to specify the ssh scheme as this is the only way to pass a custom
                    // port.
                    "ssh://$userPart$hostnamePart$portPart$serverPath"
            }
        }
        Protocol.Https -> {
            val portPart =
                if (serverPort == "443" || serverPort.isEmpty()) "" else ":$serverPort"
            val pathPart = serverPath.trimStart('/', ':')
            val urlWithFreeEntryScheme = "$hostnamePart$portPart/$pathPart"
            val url = when {
                urlWithFreeEntryScheme.startsWith("https://") -> urlWithFreeEntryScheme
                urlWithFreeEntryScheme.startsWith("http://") -> urlWithFreeEntryScheme.replaceFirst("http", "https")
                else -> "https://$urlWithFreeEntryScheme"
            }
            try {
                if (URI(url).rawAuthority != null)
                    url
                else
                    null
            } catch (_: Exception) {
                null
            }
        }
    }

    context.sharedPrefs.edit {
        remove(PreferenceKeys.GIT_REMOTE_LOCATION)
        remove(PreferenceKeys.GIT_REMOTE_PORT)
        remove(PreferenceKeys.GIT_REMOTE_SERVER)
        remove(PreferenceKeys.GIT_REMOTE_USERNAME)
    }
    if (url == null || GitSettings.updateConnectionSettingsIfValid(
            newProtocol = protocol,
            newConnectionMode = GitSettings.connectionMode,
            newUrl = url,
            newBranch = GitSettings.branch) != GitSettings.UpdateConnectionSettingsResult.Valid) {
        e { "Failed to migrate to URL-based Git config, generated URL is invalid" }
    }
}


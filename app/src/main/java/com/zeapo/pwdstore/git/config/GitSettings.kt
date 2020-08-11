/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import androidx.core.content.edit
import com.zeapo.pwdstore.Application
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs
import java.io.File
import org.eclipse.jgit.transport.URIish

enum class Protocol(val pref: String) {
    Ssh("ssh://"),
    Https("https://"),
    ;

    companion object {

        private val map = values().associateBy(Protocol::pref)
        fun fromString(type: String?): Protocol {
            return map[type ?: return Ssh]
                ?: throw IllegalArgumentException("$type is not a valid Protocol")
        }
    }
}

enum class ConnectionMode(val pref: String) {
    SshKey("ssh-key"),
    Password("username/password"),
    OpenKeychain("OpenKeychain"),
    None("None"),
    ;

    companion object {

        private val map = values().associateBy(ConnectionMode::pref)
        fun fromString(type: String?): ConnectionMode {
            return map[type ?: return SshKey]
                ?: throw IllegalArgumentException("$type is not a valid ConnectionMode")
        }
    }
}

object GitSettings {

    private const val DEFAULT_BRANCH = "master"

    private val settings by lazy { Application.instance.sharedPrefs }
    private val encryptedSettings by lazy { Application.instance.getEncryptedPrefs("git_operation") }

    var protocol
        get() = Protocol.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_PROTOCOL))
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, value.pref)
            }
        }
    var connectionMode
        get() = ConnectionMode.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_AUTH))
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_AUTH, value.pref)
            }
        }
    var url
        get() = settings.getString(PreferenceKeys.GIT_REMOTE_URL)
        private set(value) {
            require(value != null)
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_URL, value)
            }
            if (PasswordRepository.isInitialized)
                PasswordRepository.addRemote("origin", value, true)
            // When the server changes, remote password and host key file should be deleted.
            encryptedSettings.edit { remove(PreferenceKeys.HTTPS_PASSWORD) }
            File("${Application.instance.filesDir}/.host_key").delete()
        }
    var authorName
        get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME) ?: ""
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME, value)
            }
        }
    var authorEmail
        get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL) ?: ""
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL, value)
            }
        }
    var branch
        get() = settings.getString(PreferenceKeys.GIT_BRANCH_NAME) ?: DEFAULT_BRANCH
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_BRANCH_NAME, value)
            }
        }

    fun updateUrlIfValid(newUrl: String): Boolean {
        try {
            URIish(newUrl)
        } catch (_: Exception) {
            return false
        }
        if (newUrl != url)
            url = newUrl
        return true
    }
}

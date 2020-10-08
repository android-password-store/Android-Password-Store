/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import androidx.core.content.edit
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.zeapo.pwdstore.Application
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedGitPrefs
import com.zeapo.pwdstore.utils.getEncryptedProxyPrefs
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

enum class AuthMode(val pref: String) {
    SshKey("ssh-key"),
    Password("username/password"),
    OpenKeychain("OpenKeychain"),
    None("None"),
    ;

    companion object {

        private val map = values().associateBy(AuthMode::pref)
        fun fromString(type: String?): AuthMode {
            return map[type ?: return SshKey]
                ?: throw IllegalArgumentException("$type is not a valid AuthMode")
        }
    }
}

object GitSettings {

    private const val DEFAULT_BRANCH = "master"

    private val settings by lazy(LazyThreadSafetyMode.PUBLICATION) { Application.instance.sharedPrefs }
    private val encryptedSettings by lazy(LazyThreadSafetyMode.PUBLICATION) { Application.instance.getEncryptedGitPrefs() }
    private val proxySettings by lazy(LazyThreadSafetyMode.PUBLICATION) { Application.instance.getEncryptedProxyPrefs() }

    var authMode
        get() = AuthMode.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_AUTH))
        private set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_AUTH, value.pref)
            }
        }
    var url
        get() = settings.getString(PreferenceKeys.GIT_REMOTE_URL)
        private set(value) {
            require(value != null)
            if (value == url)
                return
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_URL, value)
            }
            if (PasswordRepository.isInitialized)
                PasswordRepository.addRemote("origin", value, true)
            // When the server changes, remote password, multiplexing support and host key file
            // should be deleted/reset.
            useMultiplexing = true
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
        private set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_BRANCH_NAME, value)
            }
        }
    var useMultiplexing
        get() = settings.getBoolean(PreferenceKeys.GIT_REMOTE_USE_MULTIPLEXING, true)
        set(value) {
            settings.edit {
                putBoolean(PreferenceKeys.GIT_REMOTE_USE_MULTIPLEXING, value)
            }
        }

    var proxyHost
        get() = proxySettings.getString(PreferenceKeys.PROXY_HOST)
        set(value) {
            proxySettings.edit {
                putString(PreferenceKeys.PROXY_HOST, value)
            }
        }

    var proxyPort
        get() = proxySettings.getInt(PreferenceKeys.PROXY_PORT, -1)
        set(value) {
            proxySettings.edit {
                putInt(PreferenceKeys.PROXY_PORT, value)
            }
        }

    var proxyUsername
        get() = settings.getString(PreferenceKeys.PROXY_USERNAME)
        set(value) {
            proxySettings.edit {
                putString(PreferenceKeys.PROXY_USERNAME, value)
            }
        }

    var proxyPassword
        get() = proxySettings.getString(PreferenceKeys.PROXY_PASSWORD)
        set(value) {
            proxySettings.edit {
                putString(PreferenceKeys.PROXY_PASSWORD, value)
            }
        }

    sealed class UpdateConnectionSettingsResult {
        class MissingUsername(val newProtocol: Protocol) : UpdateConnectionSettingsResult()
        class AuthModeMismatch(val newProtocol: Protocol, val validModes: List<AuthMode>) : UpdateConnectionSettingsResult()
        object Valid : UpdateConnectionSettingsResult()
        object FailedToParseUrl : UpdateConnectionSettingsResult()
    }

    fun updateConnectionSettingsIfValid(newAuthMode: AuthMode, newUrl: String, newBranch: String): UpdateConnectionSettingsResult {
        val parsedUrl = runCatching {
            URIish(newUrl)
        }.getOrElse {
            return UpdateConnectionSettingsResult.FailedToParseUrl
        }
        val newProtocol = when (parsedUrl.scheme) {
            in listOf("http", "https") -> Protocol.Https
            in listOf("ssh", null) -> Protocol.Ssh
            else -> return UpdateConnectionSettingsResult.FailedToParseUrl
        }
        if (newAuthMode != AuthMode.None && parsedUrl.user.isNullOrBlank())
            return UpdateConnectionSettingsResult.MissingUsername(newProtocol)

        val validHttpsAuth = listOf(AuthMode.None, AuthMode.Password)
        val validSshAuth = listOf(AuthMode.OpenKeychain, AuthMode.Password, AuthMode.SshKey)
        when {
            newProtocol == Protocol.Https && newAuthMode !in validHttpsAuth -> {
                return UpdateConnectionSettingsResult.AuthModeMismatch(newProtocol, validHttpsAuth)
            }
            newProtocol == Protocol.Ssh && newAuthMode !in validSshAuth -> {
                return UpdateConnectionSettingsResult.AuthModeMismatch(newProtocol, validSshAuth)
            }
        }

        url = newUrl
        authMode = newAuthMode
        branch = newBranch
        return UpdateConnectionSettingsResult.Valid
    }
}

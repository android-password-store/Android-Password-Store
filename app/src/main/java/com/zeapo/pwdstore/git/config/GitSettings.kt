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
import com.zeapo.pwdstore.utils.sharedPrefs
import java.io.File
import org.eclipse.jgit.transport.URIish

object GitSettings {

    private val settings by lazy { Application.instance.sharedPrefs }
    private val encryptedSettings by lazy { Application.instance.getEncryptedPrefs("git_operation") }

    var protocol
        get() = Protocol.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_PROTOCOL, null))
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, value.pref)
            }
        }
    var connectionMode
        get() = ConnectionMode.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_AUTH, null))
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_REMOTE_AUTH, value.pref)
            }
        }
    var url
        get() = settings.getString(PreferenceKeys.GIT_REMOTE_URL, null)
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
        get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME, null) ?: ""
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME, value)
            }
        }
    var authorEmail
        get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL, null) ?: ""
        set(value) {
            settings.edit {
                putString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL, value)
            }
        }
    var branch
        get() = settings.getString(PreferenceKeys.GIT_BRANCH_NAME, null) ?: "master"
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

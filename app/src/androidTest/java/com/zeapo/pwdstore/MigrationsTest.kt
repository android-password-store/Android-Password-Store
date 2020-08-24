/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("DEPRECATION")
package com.zeapo.pwdstore

import android.content.Context
import androidx.core.content.edit
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs
import org.junit.Test

import org.junit.Assert.*

class MigrationsTest {

    private fun checkOldKeysAreRemoved(context: Context) = with(context.sharedPrefs) {
        assertNull(getString(PreferenceKeys.GIT_REMOTE_PORT))
        assertNull(getString(PreferenceKeys.GIT_REMOTE_USERNAME))
        assertNull(getString(PreferenceKeys.GIT_REMOTE_SERVER))
        assertNull(getString(PreferenceKeys.GIT_REMOTE_LOCATION))
    }

    @Test
    fun verifySshWithCustomPortMigration() {
        val context = Application.instance.applicationContext
        context.sharedPrefs.edit {
            clear()
            putString(PreferenceKeys.GIT_REMOTE_PORT, "2200")
            putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
            putString(PreferenceKeys.GIT_REMOTE_LOCATION, "/mnt/disk3/pass-repo")
            putString(PreferenceKeys.GIT_REMOTE_SERVER, "192.168.0.102")
            putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Ssh.pref)
            putString(PreferenceKeys.GIT_REMOTE_AUTH, ConnectionMode.Password.pref)
        }
        runMigrations(context)
        checkOldKeysAreRemoved(context)
        assertEquals(
            context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
            "ssh://msfjarvis@192.168.0.102:2200/mnt/disk3/pass-repo"
        )
    }

    @Test
    fun verifySshWithDefaultPortMigration() {
        val context = Application.instance.applicationContext
        context.sharedPrefs.edit {
            clear()
            putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
            putString(PreferenceKeys.GIT_REMOTE_LOCATION, "/mnt/disk3/pass-repo")
            putString(PreferenceKeys.GIT_REMOTE_SERVER, "192.168.0.102")
            putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Ssh.pref)
            putString(PreferenceKeys.GIT_REMOTE_AUTH, ConnectionMode.SshKey.pref)
        }
        runMigrations(context)
        checkOldKeysAreRemoved(context)
        assertEquals(
            context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
            "msfjarvis@192.168.0.102:/mnt/disk3/pass-repo"
        )
    }

    @Test
    fun verifyHttpsWithGitHubMigration() {
        val context = Application.instance.applicationContext
        context.sharedPrefs.edit {
            clear()
            putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
            putString(PreferenceKeys.GIT_REMOTE_LOCATION, "Android-Password-Store/pass-test")
            putString(PreferenceKeys.GIT_REMOTE_SERVER, "github.com")
            putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Https.pref)
            putString(PreferenceKeys.GIT_REMOTE_AUTH, ConnectionMode.None.pref)
        }
        runMigrations(context)
        checkOldKeysAreRemoved(context)
        assertEquals(
            context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
            "https://github.com/Android-Password-Store/pass-test"
        )
    }
}

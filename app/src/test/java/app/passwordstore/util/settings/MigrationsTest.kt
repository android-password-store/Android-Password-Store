/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("DEPRECATION")

package app.passwordstore.util.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.passwordstore.util.extensions.getString
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MigrationsTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var filesDir: String
  private lateinit var sharedPrefs: SharedPreferences
  private lateinit var encryptedSharedPreferences: SharedPreferences
  private lateinit var proxySharedPreferences: SharedPreferences

  @BeforeTest
  fun setup() {
    context = SPMockBuilder().createContext()
    filesDir = tempFolder.root.path
    sharedPrefs = SPMockBuilder().createSharedPreferences()
    encryptedSharedPreferences = SPMockBuilder().createSharedPreferences()
    proxySharedPreferences = SPMockBuilder().createSharedPreferences()
  }

  private fun checkOldKeysAreRemoved() =
    with(sharedPrefs) {
      assertNull(getString(PreferenceKeys.GIT_REMOTE_PORT))
      assertNull(getString(PreferenceKeys.GIT_REMOTE_USERNAME))
      assertNull(getString(PreferenceKeys.GIT_REMOTE_SERVER))
      assertNull(getString(PreferenceKeys.GIT_REMOTE_LOCATION))
      assertNull(getString(PreferenceKeys.GIT_REMOTE_PROTOCOL))
    }

  @Test
  fun verifySshWithCustomPortMigration() {
    sharedPrefs.edit {
      putString(PreferenceKeys.GIT_REMOTE_PORT, "2200")
      putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
      putString(PreferenceKeys.GIT_REMOTE_LOCATION, "/mnt/disk3/pass-repo")
      putString(PreferenceKeys.GIT_REMOTE_SERVER, "192.168.0.102")
      putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Ssh.pref)
      putString(PreferenceKeys.GIT_REMOTE_AUTH, AuthMode.Password.pref)
    }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    checkOldKeysAreRemoved()
    assertEquals(
      sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
      "ssh://msfjarvis@192.168.0.102:2200/mnt/disk3/pass-repo",
    )
  }

  @Test
  fun verifySshWithDefaultPortMigration() {
    sharedPrefs.edit {
      putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
      putString(PreferenceKeys.GIT_REMOTE_LOCATION, "/mnt/disk3/pass-repo")
      putString(PreferenceKeys.GIT_REMOTE_SERVER, "192.168.0.102")
      putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Ssh.pref)
      putString(PreferenceKeys.GIT_REMOTE_AUTH, AuthMode.SshKey.pref)
    }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    checkOldKeysAreRemoved()
    assertEquals(
      sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
      "msfjarvis@192.168.0.102:/mnt/disk3/pass-repo",
    )
  }

  @Test
  fun verifyHttpsWithGitHubMigration() {
    sharedPrefs.edit {
      putString(PreferenceKeys.GIT_REMOTE_USERNAME, "msfjarvis")
      putString(PreferenceKeys.GIT_REMOTE_LOCATION, "Android-Password-Store/pass-test")
      putString(PreferenceKeys.GIT_REMOTE_SERVER, "github.com")
      putString(PreferenceKeys.GIT_REMOTE_PROTOCOL, Protocol.Https.pref)
      putString(PreferenceKeys.GIT_REMOTE_AUTH, AuthMode.None.pref)
    }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    checkOldKeysAreRemoved()
    assertEquals(
      sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_URL),
      "https://github.com/Android-Password-Store/pass-test",
    )
  }

  @Test
  fun verifyHiddenFoldersMigrationIfDisabled() {
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertEquals(true, sharedPrefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_FOLDERS, true))
    assertEquals(false, sharedPrefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, false))
  }

  @Test
  fun verifyHiddenFoldersMigrationIfEnabled() {
    sharedPrefs.edit { putBoolean(PreferenceKeys.SHOW_HIDDEN_FOLDERS, true) }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertEquals(false, sharedPrefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_FOLDERS, false))
    assertEquals(true, sharedPrefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, false))
  }

  @Test
  fun verifyClearClipboardHistoryMigration() {
    sharedPrefs.edit { putBoolean(PreferenceKeys.CLEAR_CLIPBOARD_20X, true) }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertEquals(true, sharedPrefs.getBoolean(PreferenceKeys.CLEAR_CLIPBOARD_HISTORY, false))
    assertFalse(sharedPrefs.contains(PreferenceKeys.CLEAR_CLIPBOARD_20X))
  }

  @Test
  fun verifyClassicPasswordGeneratorMigration() {
    sharedPrefs.edit { putString(PreferenceKeys.PREF_KEY_PWGEN_TYPE, "classic") }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertEquals("classic", sharedPrefs.getString(PreferenceKeys.PREF_KEY_PWGEN_TYPE))
  }

  @Test
  fun verifyXkPasswdPasswordGeneratorMigration() {
    sharedPrefs.edit { putString(PreferenceKeys.PREF_KEY_PWGEN_TYPE, "xkpasswd") }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertEquals("diceware", sharedPrefs.getString(PreferenceKeys.PREF_KEY_PWGEN_TYPE))
  }

  @Test
  fun verifyExternalStorageMigration() {
    sharedPrefs.edit {
      putBoolean(PreferenceKeys.GIT_EXTERNAL, true)
      putString(PreferenceKeys.GIT_EXTERNAL_REPO, "/sdcard/")
    }
    runMigrations(
      filesDir,
      sharedPrefs,
      GitSettings(sharedPrefs, encryptedSharedPreferences, proxySharedPreferences, filesDir),
    )
    assertFalse { sharedPrefs.contains(PreferenceKeys.GIT_EXTERNAL) }
    assertFalse { sharedPrefs.contains(PreferenceKeys.GIT_EXTERNAL_REPO) }
    assertTrue { sharedPrefs.getBoolean(PreferenceKeys.GIT_EXTERNAL_MIGRATED, false) }
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.injection.context.FilesDirPath
import dev.msfjarvis.aps.injection.prefs.GitPreferences
import dev.msfjarvis.aps.injection.prefs.ProxyPreferences
import dev.msfjarvis.aps.injection.prefs.SettingsPreferences
import dev.msfjarvis.aps.util.extensions.getString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
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

@Singleton
class GitSettings @Inject constructor(
  @SettingsPreferences private val settings: SharedPreferences,
  @GitPreferences private val encryptedSettings: SharedPreferences,
  @ProxyPreferences private val proxySettings: SharedPreferences,
  @FilesDirPath private val filesDirPath: String,
) {

  private val hostKeyPath by lazy(LazyThreadSafetyMode.NONE) {
    "$filesDirPath/.host_key"
  }
  var authMode
    get() = AuthMode.fromString(settings.getString(PreferenceKeys.GIT_REMOTE_AUTH))
    private set(value) {
      settings.edit { putString(PreferenceKeys.GIT_REMOTE_AUTH, value.pref) }
    }
  var url
    get() = settings.getString(PreferenceKeys.GIT_REMOTE_URL)
    private set(value) {
      require(value != null)
      if (value == url) return
      settings.edit { putString(PreferenceKeys.GIT_REMOTE_URL, value) }
      if (PasswordRepository.isInitialized) PasswordRepository.addRemote("origin", value, true)
      // When the server changes, remote password, multiplexing support and host key file
      // should be deleted/reset.
      useMultiplexing = true
      encryptedSettings.edit { remove(PreferenceKeys.HTTPS_PASSWORD) }
      clearSavedHostKey()
    }
  var authorName
    get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME) ?: ""
    set(value) {
      settings.edit { putString(PreferenceKeys.GIT_CONFIG_AUTHOR_NAME, value) }
    }
  var authorEmail
    get() = settings.getString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL) ?: ""
    set(value) {
      settings.edit { putString(PreferenceKeys.GIT_CONFIG_AUTHOR_EMAIL, value) }
    }
  var branch
    get() = settings.getString(PreferenceKeys.GIT_BRANCH_NAME) ?: DEFAULT_BRANCH
    private set(value) {
      settings.edit { putString(PreferenceKeys.GIT_BRANCH_NAME, value) }
    }
  var useMultiplexing
    get() = settings.getBoolean(PreferenceKeys.GIT_REMOTE_USE_MULTIPLEXING, true)
    set(value) {
      settings.edit { putBoolean(PreferenceKeys.GIT_REMOTE_USE_MULTIPLEXING, value) }
    }
  var proxyHost
    get() = proxySettings.getString(PreferenceKeys.PROXY_HOST)
    set(value) {
      proxySettings.edit { putString(PreferenceKeys.PROXY_HOST, value) }
    }
  var proxyPort
    get() = proxySettings.getInt(PreferenceKeys.PROXY_PORT, -1)
    set(value) {
      proxySettings.edit { putInt(PreferenceKeys.PROXY_PORT, value) }
    }
  var proxyUsername
    get() = settings.getString(PreferenceKeys.PROXY_USERNAME)
    set(value) {
      proxySettings.edit { putString(PreferenceKeys.PROXY_USERNAME, value) }
    }
  var proxyPassword
    get() = proxySettings.getString(PreferenceKeys.PROXY_PASSWORD)
    set(value) {
      proxySettings.edit { putString(PreferenceKeys.PROXY_PASSWORD, value) }
    }
  var rebaseOnPull
    get() = settings.getBoolean(PreferenceKeys.REBASE_ON_PULL, true)
    set(value) {
      settings.edit { putBoolean(PreferenceKeys.REBASE_ON_PULL, value) }
    }

  sealed class UpdateConnectionSettingsResult {
    class MissingUsername(val newProtocol: Protocol) : UpdateConnectionSettingsResult()
    class AuthModeMismatch(val newProtocol: Protocol, val validModes: List<AuthMode>) : UpdateConnectionSettingsResult()
    object Valid : UpdateConnectionSettingsResult()
    object FailedToParseUrl : UpdateConnectionSettingsResult()
  }

  fun updateConnectionSettingsIfValid(
    newAuthMode: AuthMode,
    newUrl: String,
    newBranch: String
  ): UpdateConnectionSettingsResult {
    val parsedUrl =
      runCatching { URIish(newUrl) }.getOrElse {
        return UpdateConnectionSettingsResult.FailedToParseUrl
      }
    val newProtocol =
      when (parsedUrl.scheme) {
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

  /** Deletes a previously saved SSH host key */
  fun clearSavedHostKey() {
    File(hostKeyPath).delete()
  }

  /** Returns true if a host key was previously saved */
  fun hasSavedHostKey(): Boolean = File(hostKeyPath).exists()

  companion object {
    private const val DEFAULT_BRANCH = "master"
  }
}

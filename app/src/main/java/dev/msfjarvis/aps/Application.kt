/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps

import android.content.SharedPreferences
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import com.github.ajalt.timberkt.Timber.DebugTree
import com.github.ajalt.timberkt.Timber.plant
import dagger.hilt.android.HiltAndroidApp
import dev.msfjarvis.aps.injection.context.FilesDirPath
import dev.msfjarvis.aps.injection.prefs.SettingsPreferences
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.git.sshj.setUpBouncyCastleForSshj
import dev.msfjarvis.aps.util.proxy.ProxyUtils
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import dev.msfjarvis.aps.util.settings.runMigrations
import javax.inject.Inject

@Suppress("Unused")
@HiltAndroidApp
class Application : android.app.Application(), SharedPreferences.OnSharedPreferenceChangeListener {

  @Inject @SettingsPreferences lateinit var prefs: SharedPreferences
  @Inject @FilesDirPath lateinit var filesDirPath: String
  @Inject lateinit var proxyUtils: ProxyUtils
  @Inject lateinit var gitSettings: GitSettings

  override fun onCreate() {
    super.onCreate()
    instance = this
    if (BuildConfig.ENABLE_DEBUG_FEATURES ||
        prefs.getBoolean(PreferenceKeys.ENABLE_DEBUG_LOGGING, false)
    ) {
      plant(DebugTree())
      StrictMode.setVmPolicy(VmPolicy.Builder().detectAll().penaltyLog().build())
      StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectAll().penaltyLog().build())
    }
    prefs.registerOnSharedPreferenceChangeListener(this)
    setNightMode()
    setUpBouncyCastleForSshj()
    runMigrations(filesDirPath, prefs, gitSettings)
    proxyUtils.setDefaultProxy()
  }

  override fun onTerminate() {
    prefs.unregisterOnSharedPreferenceChangeListener(this)
    super.onTerminate()
  }

  override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
    if (key == PreferenceKeys.APP_THEME) {
      setNightMode()
    }
  }

  private fun setNightMode() {
    AppCompatDelegate.setDefaultNightMode(
      when (prefs.getString(PreferenceKeys.APP_THEME) ?: getString(R.string.app_theme_def)) {
        "light" -> MODE_NIGHT_NO
        "dark" -> MODE_NIGHT_YES
        "follow_system" -> MODE_NIGHT_FOLLOW_SYSTEM
        else -> MODE_NIGHT_AUTO_BATTERY
      }
    )
  }

  companion object {

    lateinit var instance: Application
  }
}

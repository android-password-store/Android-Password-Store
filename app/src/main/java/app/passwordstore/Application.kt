/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore

import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate.*
import app.passwordstore.injection.context.FilesDirPath
import app.passwordstore.injection.prefs.SettingsPreferences
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.features.Feature
import app.passwordstore.util.features.Features
import app.passwordstore.util.git.sshj.setUpBouncyCastleForSshj
import app.passwordstore.util.leaks.SentryLeakUploader
import app.passwordstore.util.proxy.ProxyUtils
import app.passwordstore.util.settings.GitSettings
import app.passwordstore.util.settings.PreferenceKeys
import app.passwordstore.util.settings.runMigrations
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.protocol.User
import java.util.concurrent.Executors
import javax.inject.Inject
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import logcat.AndroidLogcatLogger
import logcat.LogPriority.DEBUG
import logcat.LogPriority.VERBOSE
import logcat.LogcatLogger
import logcat.logcat

@Suppress("Unused")
@HiltAndroidApp
class Application : android.app.Application(), SharedPreferences.OnSharedPreferenceChangeListener {

  @Inject @SettingsPreferences lateinit var prefs: SharedPreferences
  @Inject @FilesDirPath lateinit var filesDirPath: String
  @Inject lateinit var proxyUtils: ProxyUtils
  @Inject lateinit var gitSettings: GitSettings
  @Inject lateinit var features: Features

  override fun onCreate() {
    super.onCreate()
    instance = this
    val eventListeners = LeakCanary.config.eventListeners + SentryLeakUploader
    if (
      BuildConfig.ENABLE_DEBUG_FEATURES ||
        prefs.getBoolean(PreferenceKeys.ENABLE_DEBUG_LOGGING, false)
    ) {
      LogcatLogger.install(AndroidLogcatLogger(DEBUG))
      setVmPolicy()
      AppWatcher.manualInstall(this)
      LeakCanary.config = LeakCanary.config.copy(dumpHeap = true, eventListeners = eventListeners)
      LeakCanary.showLeakDisplayActivityLauncherIcon(true)
    } else {
      LeakCanary.config = LeakCanary.config.copy(dumpHeap = false, eventListeners = eventListeners)
    }
    prefs.registerOnSharedPreferenceChangeListener(this)
    setNightMode()
    setUpBouncyCastleForSshj()
    runMigrations(filesDirPath, prefs, gitSettings)
    proxyUtils.setDefaultProxy()
    DynamicColors.applyToActivitiesIfAvailable(this)
    Sentry.configureScope { scope ->
      val user = User()
      user.data =
        Feature.entries.associate { feature ->
          "features.${feature.configKey}" to features.isEnabled(feature).toString()
        }
      scope.user = user
    }
  }

  override fun onTerminate() {
    prefs.unregisterOnSharedPreferenceChangeListener(this)
    super.onTerminate()
  }

  override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
    if (key == PreferenceKeys.APP_THEME) {
      setNightMode()
    }
  }

  private fun setVmPolicy() {
    val builder =
      StrictMode.VmPolicy.Builder()
        .detectActivityLeaks()
        .detectCleartextNetwork()
        .detectFileUriExposure()
        .detectLeakedClosableObjects()
        .detectLeakedRegistrationObjects()
        .detectLeakedSqlLiteObjects()

    builder.detectContentUriWithoutPermission()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      builder.detectCredentialProtectedWhileLocked().detectImplicitDirectBoot()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      builder.detectNonSdkApiUsage()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.detectIncorrectContextUse().detectUnsafeIntentLaunch()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      builder.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
        logcat(VERBOSE) { violation.stackTraceToString() }
      }
    } else {
      builder.penaltyLog()
    }

    val policy = builder.build()
    StrictMode.setVmPolicy(policy)
  }

  private fun setNightMode() {
    setDefaultNightMode(
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

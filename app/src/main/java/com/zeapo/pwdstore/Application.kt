/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.DebugTree
import com.github.ajalt.timberkt.Timber.plant
import com.zeapo.pwdstore.git.config.setUpBouncyCastleForSshj
import com.zeapo.pwdstore.utils.PreferenceKeys

@Suppress("Unused")
class Application : android.app.Application(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var prefs: SharedPreferences? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (BuildConfig.ENABLE_DEBUG_FEATURES || prefs?.getBoolean(PreferenceKeys.ENABLE_DEBUG_LOGGING, false) ==
            true) {
            plant(DebugTree())
        }
        prefs?.registerOnSharedPreferenceChangeListener(this)
        setNightMode()
        setUpBouncyCastleForSshj()
    }

    override fun onTerminate() {
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
        super.onTerminate()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        if (key == PreferenceKeys.APP_THEME) {
            setNightMode()
        }
    }

    private fun setNightMode() {
        AppCompatDelegate.setDefaultNightMode(when (prefs?.getString(PreferenceKeys.APP_THEME, getString(R.string.app_theme_def))) {
            "light" -> MODE_NIGHT_NO
            "dark" -> MODE_NIGHT_YES
            "follow_system" -> MODE_NIGHT_FOLLOW_SYSTEM
            else -> MODE_NIGHT_AUTO_BATTERY
        })
    }
}

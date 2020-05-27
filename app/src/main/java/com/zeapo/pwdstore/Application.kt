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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@Suppress("Unused")
class Application : android.app.Application(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var prefs: SharedPreferences? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (BuildConfig.ENABLE_DEBUG_FEATURES || prefs?.getBoolean("enable_debug_logging", false) == true) {
            plant(DebugTree())
        }
        prefs?.registerOnSharedPreferenceChangeListener(this)
        setNightMode()
        setUpBouncyCastle()
    }

    override fun onTerminate() {
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
        super.onTerminate()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        if (key == "app_theme") {
            setNightMode()
        }
    }

    private fun setUpBouncyCastle() {
        // Replace the Android BC provider with the Java BouncyCastle provider since the former does
        // not include all the required algorithms.
        // TODO: Verify that we are indeed using the fast Android-native implementation whenever
        //  possible.
        // Note: This may affect crypto operations in other parts of the application.
        val bcIndex = Security.getProviders().indexOfFirst {
            it.name == BouncyCastleProvider.PROVIDER_NAME
        }
        if (bcIndex == -1) {
            // No Android BC found, install Java BC at lowest priority.
            Security.addProvider(BouncyCastleProvider())
        } else {
            // Replace Android BC with Java BC, inserted at the same position.
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), bcIndex + 1)
        }
    }

    private fun setNightMode() {
        AppCompatDelegate.setDefaultNightMode(when (prefs?.getString("app_theme", getString(R.string.app_theme_def))) {
            "light" -> MODE_NIGHT_NO
            "dark" -> MODE_NIGHT_YES
            "follow_system" -> MODE_NIGHT_FOLLOW_SYSTEM
            else -> MODE_NIGHT_AUTO_BATTERY
        })
    }
}

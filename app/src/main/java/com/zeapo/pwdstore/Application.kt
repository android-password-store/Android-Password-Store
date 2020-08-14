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
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.ajalt.timberkt.Timber.DebugTree
import com.github.ajalt.timberkt.Timber.plant
import com.zeapo.pwdstore.git.sshj.setUpBouncyCastleForSshj
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs

@Suppress("Unused")
class Application : android.app.Application(), SharedPreferences.OnSharedPreferenceChangeListener {

    var requiresAuthentication = true;
    var isAuthenticating = false;
    var isAuthenticationEnabled = false;

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.ENABLE_DEBUG_FEATURES ||
            sharedPrefs.getBoolean(PreferenceKeys.ENABLE_DEBUG_LOGGING, false)) {
            plant(DebugTree())
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        isAuthenticationEnabled = sharedPrefs.getBoolean(PreferenceKeys.BIOMETRIC_AUTH, false);
        setNightMode()
        setUpBouncyCastleForSshj()
        runMigrations(applicationContext)
        ProcessLifecycleOwner.get().lifecycle.addObserver(BaseActivity.ProcessLifecycleObserver(this))
    }

    override fun onTerminate() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onTerminate()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        when (key) {
            PreferenceKeys.APP_THEME -> setNightMode()
            PreferenceKeys.BIOMETRIC_AUTH -> isAuthenticationEnabled = sharedPrefs.getBoolean(key, false);
        }
    }

    private fun setNightMode() {
        AppCompatDelegate.setDefaultNightMode(when (sharedPrefs.getString(PreferenceKeys.APP_THEME)
            ?: getString(R.string.app_theme_def)) {
            "light" -> MODE_NIGHT_NO
            "dark" -> MODE_NIGHT_YES
            "follow_system" -> MODE_NIGHT_FOLLOW_SYSTEM
            else -> MODE_NIGHT_AUTO_BATTERY
        })
    }

    companion object {

        lateinit var instance: Application
    }
}

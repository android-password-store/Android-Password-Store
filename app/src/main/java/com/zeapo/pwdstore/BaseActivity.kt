package com.zeapo.pwdstore

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.ajalt.timberkt.Timber.d
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.sharedPrefs

abstract class BaseActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        val prefs = sharedPrefs
        if (AuthManager.shouldAuthenticate()) {
            val view = findViewById<View>(android.R.id.content)
            view.isVisible = false
            AuthManager.skipAuth = true
            BiometricAuthenticator.authenticate(this) {
                view.isVisible = true
                when (it) {
                    is BiometricAuthenticator.Result.Success -> {
                        AuthManager.doOnSuccess()
                    }
                    is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> {
                        prefs.edit { remove(PreferenceKeys.BIOMETRIC_AUTH) }
                        AuthManager.doOnSuccess()
                    }
                    is BiometricAuthenticator.Result.Failure, BiometricAuthenticator.Result.Cancelled -> {
                        AuthManager.doOnFailure()
                        finishAffinity()
                    }
                }
            }
        }
    }

    class ProcessLifecycleObserver : DefaultLifecycleObserver {

        override fun onStop(owner: LifecycleOwner) {
            AuthManager.doOnBackground()
            super.onStop(owner)
        }
    }
}


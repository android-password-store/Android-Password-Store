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

    val application by lazy { getApplication() as Application }

    override fun onResume() {
        super.onResume()
        val prefs = sharedPrefs
        d { "requiresAuthentication : ${application.requiresAuthentication}  isAuthenticating : ${application.isAuthenticating} " }
        if (application.isAuthenticationEnabled && application.requiresAuthentication && !application.isAuthenticating) {
            val view = findViewById<View>(android.R.id.content)
            view.isVisible = false
            application.isAuthenticating = true
            BiometricAuthenticator.authenticate(this) {
                view.isVisible = true
                when (it) {
                    is BiometricAuthenticator.Result.Success -> {
                        application.isAuthenticating = false
                        application.requiresAuthentication = false
                    }
                    is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> {
                        prefs.edit { remove(PreferenceKeys.BIOMETRIC_AUTH) }
                        application.isAuthenticating = false
                        application.requiresAuthentication = false
                    }
                    is BiometricAuthenticator.Result.Failure, BiometricAuthenticator.Result.Cancelled -> {
                        application.isAuthenticating = false
                        application.requiresAuthentication = true
                        finishAffinity()
                    }
                }
            }
        }
    }

    private fun shouldAuthenticate() {

    }

    class ProcessLifecycleObserver(private val application: Application): DefaultLifecycleObserver {

        override fun onStop(owner: LifecycleOwner) {
            application.requiresAuthentication = true
            super.onStop(owner)
        }
    }
}


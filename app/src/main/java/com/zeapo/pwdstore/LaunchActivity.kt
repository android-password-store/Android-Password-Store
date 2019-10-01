package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.utils.auth.AuthenticationResult
import com.zeapo.pwdstore.utils.auth.Authenticator

class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("biometric_auth", false)) {
            Authenticator(this) {
                when (it) {
                    is AuthenticationResult.Success -> {
                        startMainActivity()
                    }
                    is AuthenticationResult.UnrecoverableError -> {
                        finish()
                    }
                    else -> {
                    }
                }
            }.authenticate()
        } else {
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, PasswordStore::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

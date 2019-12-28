package com.passwordstore.android.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.passwordstore.android.PasswordStoreApplication
import com.passwordstore.android.utils.AuthenticationResult
import com.passwordstore.android.utils.Authenticator

abstract class BaseActivity: AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        if (PasswordStoreApplication.needAuthentication) {
            Authenticator(this) {
                when (it) {
                    is AuthenticationResult.Success -> {
                        Toast.makeText(this, "Auth Success", Toast.LENGTH_SHORT).show()
                        PasswordStoreApplication.needAuthentication = false
                    }
                    is AuthenticationResult.UnrecoverableError -> {
                        Toast.makeText(this, "Auth Fail", Toast.LENGTH_SHORT).show()
                        finish()
                        finishAffinity()
                    }
                    else -> {
                    }
                }
            }.authenticate()
        }
    }
}

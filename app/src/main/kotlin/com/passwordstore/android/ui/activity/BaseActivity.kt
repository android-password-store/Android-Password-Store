/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.ui.activity

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.passwordstore.android.PasswordStoreApplication
import com.passwordstore.android.utils.AuthenticationResult
import com.passwordstore.android.utils.Authenticator

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var application: PasswordStoreApplication

    override fun onResume() {
        super.onResume()
        application = getApplication() as PasswordStoreApplication
        if (application.requiresAuthentication.value != false) {
            Authenticator(this) {
                when (it) {
                    is AuthenticationResult.Success -> {
                        application.requiresAuthentication.postValue(false)
                    }
                    is AuthenticationResult.UnrecoverableError -> {
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
                        finishAffinity()
                    }
                    else -> {
                    }
                }
            }.authenticate()
        }
    }
}

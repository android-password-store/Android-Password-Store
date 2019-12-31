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

    private val passwordStoreApplication by lazy { application as PasswordStoreApplication }

    override fun onResume() {
        super.onResume()
        if (passwordStoreApplication.requiresAuthentication.value != false) {
            Authenticator(this) {
                when (it) {
                    is AuthenticationResult.Success -> {
                        passwordStoreApplication.requiresAuthentication.postValue(false)
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

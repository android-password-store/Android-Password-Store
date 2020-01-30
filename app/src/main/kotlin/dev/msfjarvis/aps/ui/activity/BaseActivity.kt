/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.activity

import androidx.appcompat.app.AppCompatActivity
import dev.msfjarvis.aps.PasswordStoreApplication
import dev.msfjarvis.aps.utils.AuthenticationResult
import dev.msfjarvis.aps.utils.Authenticator

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
                        application.requiresAuthentication.postValue(false)
                    }
                    else -> {
                    }
                }
            }.authenticate()
        }
    }
}

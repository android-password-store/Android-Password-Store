package com.zeapo.pwdstore

import com.github.ajalt.timberkt.Timber.d

object AuthManager {

    private var isAuthRequired = true

    var isAuthEnabled = false
    var skipAuth = false

    fun doOnBackground() {
        if (skipAuth) {
            isAuthRequired = false
            skipAuth = false
        } else {
            isAuthRequired = true
        }
    }

    fun doOnSuccess() {
        skipAuth = false
        isAuthRequired = false
    }

    fun doOnFailure() {
        skipAuth = false
        isAuthRequired = true
    }

    fun shouldAuthenticate() : Boolean {
        return isAuthEnabled && isAuthRequired && !skipAuth
    }
}
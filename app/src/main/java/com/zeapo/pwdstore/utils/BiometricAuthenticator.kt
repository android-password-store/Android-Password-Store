/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.app.KeyguardManager
import android.os.Handler
import androidx.annotation.StringRes
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.d
import com.zeapo.pwdstore.R

object BiometricAuthenticator {
    private const val TAG = "BiometricAuthenticator"
    private val handler = Handler()

    sealed class Result {
        data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : Result()
        data class Failure(val code: Int?, val message: CharSequence) : Result()
        object HardwareUnavailableOrDisabled : Result()
        object Cancelled : Result()
    }

    fun authenticate(
        activity: FragmentActivity,
        @StringRes dialogTitleRes: Int = R.string.biometric_prompt_title,
        callback: (Result) -> Unit
    ) {
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                tag(TAG).d { "BiometricAuthentication error: errorCode=$errorCode, msg=$errString" }
                callback(when (errorCode) {
                    BiometricConstants.ERROR_CANCELED, BiometricConstants.ERROR_USER_CANCELED,
                    BiometricConstants.ERROR_NEGATIVE_BUTTON -> {
                        Result.Cancelled
                    }
                    BiometricConstants.ERROR_HW_NOT_PRESENT, BiometricConstants.ERROR_HW_UNAVAILABLE,
                    BiometricConstants.ERROR_NO_BIOMETRICS, BiometricConstants.ERROR_NO_DEVICE_CREDENTIAL -> {
                        Result.HardwareUnavailableOrDisabled
                    }
                    else -> Result.Failure(errorCode, activity.getString(R.string.biometric_auth_error_reason, errString))
                })
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(Result.Failure(null, activity.getString(R.string.biometric_auth_error)))
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(Result.Success(result.cryptoObject))
            }
        }
        val biometricPrompt = BiometricPrompt(activity, { handler.post(it) }, authCallback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(dialogTitleRes))
                .setDeviceCredentialAllowed(true)
                .build()
        if (BiometricManager.from(activity).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS ||
                activity.getSystemService<KeyguardManager>()!!.isDeviceSecure) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            callback(Result.HardwareUnavailableOrDisabled)
        }
    }
}

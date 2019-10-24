/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils.auth

import androidx.biometric.BiometricPrompt

internal sealed class AuthenticationResult {
    internal data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) :
            AuthenticationResult()
    internal data class RecoverableError(val code: Int, val message: CharSequence) :
            AuthenticationResult()
    internal data class UnrecoverableError(val code: Int, val message: CharSequence) :
            AuthenticationResult()
    internal object Failure : AuthenticationResult()
    internal object Cancelled : AuthenticationResult()
}

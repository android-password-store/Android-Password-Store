/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.auth

import android.app.KeyguardManager
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import app.passwordstore.R
import logcat.logcat

object BiometricAuthenticator {

  private const val TAG = "BiometricAuthenticator"
  private const val VALID_AUTHENTICATORS =
    Authenticators.DEVICE_CREDENTIAL or Authenticators.BIOMETRIC_WEAK

  /**
   * Sealed class to wrap [BiometricPrompt]'s [Int]-based return codes into more easily-interpreted
   * types.
   */
  sealed class Result {

    /** Biometric authentication was a success. */
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : Result()

    /** Biometric authentication has irreversibly failed. */
    data class Failure(val code: Int?, val message: CharSequence) : Result()

    /**
     * An incorrect biometric was entered, but the prompt UI is offering the option to retry the
     * operation.
     */
    data object Retry : Result()

    /** The biometric hardware is unavailable or disabled on a software or hardware level. */
    data object HardwareUnavailableOrDisabled : Result()

    /** The biometric prompt was canceled due to a user-initiated action. */
    data object CanceledByUser : Result()

    /** The biometric prompt was canceled by the system. */
    data object CanceledBySystem : Result()
  }

  fun canAuthenticate(activity: FragmentActivity): Boolean {
    return BiometricManager.from(activity).canAuthenticate(VALID_AUTHENTICATORS) ==
      BiometricManager.BIOMETRIC_SUCCESS
  }

  fun authenticate(
    activity: FragmentActivity,
    @StringRes dialogTitleRes: Int = R.string.biometric_prompt_title,
    callback: (Result) -> Unit,
  ) {
    val authCallback = createPromptAuthenticationCallback(activity, callback)
    val deviceHasKeyguard = activity.getSystemService<KeyguardManager>()?.isDeviceSecure == true
    if (canAuthenticate(activity) || deviceHasKeyguard) {
      val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
          .setTitle(activity.getString(dialogTitleRes))
          .setAllowedAuthenticators(VALID_AUTHENTICATORS)
          .build()
      BiometricPrompt(
          activity,
          ContextCompat.getMainExecutor(activity.applicationContext),
          authCallback,
        )
        .authenticate(promptInfo)
    } else {
      callback(Result.HardwareUnavailableOrDisabled)
    }
  }

  private fun createPromptAuthenticationCallback(
    activity: FragmentActivity,
    callback: (Result) -> Unit,
  ): BiometricPrompt.AuthenticationCallback {
    return object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        logcat(TAG) { "onAuthenticationError(errorCode=$errorCode, msg=$errString)" }
        when (errorCode) {
          /** Keep in sync with [androidx.biometric.BiometricPrompt.AuthenticationError] */
          BiometricPrompt.ERROR_HW_UNAVAILABLE -> callback(Result.HardwareUnavailableOrDisabled)
          BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> callback(Result.Retry)
          BiometricPrompt.ERROR_TIMEOUT ->
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          BiometricPrompt.ERROR_NO_SPACE ->
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          BiometricPrompt.ERROR_CANCELED -> callback(Result.CanceledBySystem)
          BiometricPrompt.ERROR_LOCKOUT ->
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          BiometricPrompt.ERROR_VENDOR ->
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          BiometricPrompt.ERROR_USER_CANCELED -> callback(Result.CanceledByUser)
          BiometricPrompt.ERROR_NO_BIOMETRICS -> callback(Result.HardwareUnavailableOrDisabled)
          BiometricPrompt.ERROR_HW_NOT_PRESENT -> callback(Result.HardwareUnavailableOrDisabled)
          BiometricPrompt.ERROR_NEGATIVE_BUTTON -> callback(Result.CanceledByUser)
          BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
            callback(Result.HardwareUnavailableOrDisabled)
          // We cover all guaranteed values above, but [errorCode] is still an Int
          // at the end of the day so a catch-all else will always be required.
          else -> {
            callback(
              Result.Failure(
                errorCode,
                activity.getString(R.string.biometric_auth_error_reason, errString),
              )
            )
          }
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        logcat(TAG) { "onAuthenticationFailed()" }
        callback(Result.Retry)
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        logcat(TAG) { "onAuthenticationSucceeded()" }
        callback(Result.Success(result.cryptoObject))
      }
    }
  }
}

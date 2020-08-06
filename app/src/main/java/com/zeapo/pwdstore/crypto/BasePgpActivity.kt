/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.ClipboardService
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.OPENPGP_PROVIDER
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.clipboard
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.snackbar
import java.io.File
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError

@Suppress("Registered")
open class BasePgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {

    /**
     * Full path to the repository
     */
    val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }

    /**
     * Full path to the password file being worked on
     */
    val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }

    /**
     * Name of the password file
     *
     * Converts personal/auth.foo.org/john_doe@example.org.gpg to john_doe.example.org
     */
    val name: String by lazy { File(fullPath).nameWithoutExtension }

    /**
     * Get the timestamp for when this file was last modified.
     */
    val lastChangedString: CharSequence by lazy {
        getLastChangedString(
            intent.getLongExtra(
                "LAST_CHANGED_TIMESTAMP",
                -1L
            )
        )
    }

    /**
     * [SharedPreferences] instance used by subclasses to persist settings
     */
    val settings: SharedPreferences by lazy { sharedPrefs }

    /**
     * Handle to the [OpenPgpApi] instance that is used by subclasses to interface with OpenKeychain.
     */
    private var serviceConnection: OpenPgpServiceConnection? = null
    var api: OpenPgpApi? = null

    /**
     * [onCreate] sets the window up with the right flags to prevent auth leaks through screenshots
     * or recent apps screen.
     */
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        tag(TAG)
    }

    /**
     * [onDestroy] handles unbinding from the OpenPgp service linked with [serviceConnection]. This
     * is annotated with [CallSuper] because it's critical to unbind the service to ensure we're not
     * leaking things.
     */
    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.unbindFromService()
    }

    /**
     * Sets up [api] once the service is bound. Downstream consumers must call super this to
     * initialize [api]
     */
    @CallSuper
    override fun onBound(service: IOpenPgpService2) {
        api = OpenPgpApi(this, service)
    }

    /**
     * Mandatory error handling from [OpenPgpServiceConnection.OnBound]. All subclasses must handle
     * their own errors, and hence this class simply logs and rethrows. Subclasses Must NOT call super.
     */
    override fun onError(e: Exception) {
        e(e) { "Callers must handle their own exceptions" }
        throw e
    }

    /**
     * Method for subclasses to initiate binding with [OpenPgpServiceConnection].
     */
    fun bindToOpenKeychain(onBoundListener: OpenPgpServiceConnection.OnBound) {
        serviceConnection = OpenPgpServiceConnection(this, OPENPGP_PROVIDER, onBoundListener)
        serviceConnection?.bindToService()
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     */
    fun getUserInteractionRequestIntent(result: Intent): IntentSender {
        i { "RESULT_CODE_USER_INTERACTION_REQUIRED" }
        return (result.getParcelableExtra(OpenPgpApi.RESULT_INTENT) as PendingIntent).intentSender
    }

    /**
     * Gets a relative string describing when this shape was last changed
     * (e.g. "one hour ago")
     */
    private fun getLastChangedString(timeStamp: Long): CharSequence {
        if (timeStamp < 0) {
            throw RuntimeException()
        }

        return DateUtils.getRelativeTimeSpanString(this, timeStamp, true)
    }

    /**
     * Base handling of OpenKeychain errors based on the error contained in [result]. Subclasses
     * can use this when they want to default to sane error handling.
     */
    fun handleError(result: Intent) {
        val error: OpenPgpError? = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)
        if (error != null) {
            when (error.errorId) {
                OpenPgpError.NO_OR_WRONG_PASSPHRASE -> {
                    snackbar(message = getString(R.string.openpgp_error_wrong_passphrase))
                }
                OpenPgpError.NO_USER_IDS -> {
                    snackbar(message = getString(R.string.openpgp_error_no_user_ids))
                }
                else -> {
                    snackbar(message = getString(R.string.openpgp_error_unknown, error.message))
                    e { "onError getErrorId: ${error.errorId}" }
                    e { "onError getMessage: ${error.message}" }
                }
            }
        }
    }

    /**
     * Copies provided [text] to the clipboard. Shows a [Snackbar] which can be disabled by passing
     * [showSnackbar] as false.
     */
    fun copyTextToClipboard(
        text: String?,
        showSnackbar: Boolean = true,
        @StringRes snackbarTextRes: Int = R.string.clipboard_copied_text
    ) {
        val clipboard = clipboard ?: return
        val clip = ClipData.newPlainText("pgp_handler_result_pm", text)
        clipboard.setPrimaryClip(clip)
        if (showSnackbar) {
            snackbar(message = resources.getString(snackbarTextRes))
        }
    }

    /**
     * Copies a provided [password] string to the clipboard. This wraps [copyTextToClipboard] to
     * hide the default [Snackbar] and starts off an instance of [ClipboardService] to provide a
     * way of clearing the clipboard.
     */
    fun copyPasswordToClipboard(password: String?) {
        copyTextToClipboard(password, showSnackbar = false)

        var clearAfter = 45
        try {
            clearAfter = (settings.getString(PreferenceKeys.GENERAL_SHOW_TIME, "45")
                ?: "45").toInt()
        } catch (_: NumberFormatException) {
        }

        if (clearAfter != 0) {
            val service = Intent(this, ClipboardService::class.java).apply {
                action = ClipboardService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }
            snackbar(message = resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            snackbar(message = resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
    }

    companion object {

        private const val TAG = "APS/BasePgpActivity"
        const val KEY_PWGEN_TYPE_CLASSIC = "classic"
        const val KEY_PWGEN_TYPE_XKPASSWD = "xkpasswd"

        /**
         * Gets the relative path to the repository
         */
        fun getRelativePath(fullPath: String, repositoryPath: String): String =
            fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")

        /**
         * Gets the Parent path, relative to the repository
         */
        fun getParentPath(fullPath: String, repositoryPath: String): String {
            val relativePath = getRelativePath(fullPath, repositoryPath)
            val index = relativePath.lastIndexOf("/")
            return "/${relativePath.substring(startIndex = 0, endIndex = index + 1)}/".replace("/+".toRegex(), "/")
        }

        /**
         * /path/to/store/social/facebook.gpg -> social/facebook
         */
        @JvmStatic
        fun getLongName(fullPath: String, repositoryPath: String, basename: String): String {
            var relativePath = getRelativePath(fullPath, repositoryPath)
            return if (relativePath.isNotEmpty() && relativePath != "/") {
                // remove preceding '/'
                relativePath = relativePath.substring(1)
                if (relativePath.endsWith('/')) {
                    relativePath + basename
                } else {
                    "$relativePath/$basename"
                }
            } else {
                basename
            }
        }
    }
}

/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.apache.commons.io.FilenameUtils
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError

@Suppress("Registered")
open class BasePgpActivity(@LayoutRes layoutRes: Int) : AppCompatActivity(layoutRes), OpenPgpServiceConnection.OnBound {

    val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }
    val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }
    val name: String by lazy { getName(fullPath) }
    val lastChangedString: CharSequence by lazy {
        getLastChangedString(
            intent.getLongExtra(
                "LAST_CHANGED_TIMESTAMP",
                -1L
            )
        )
    }

    val settings: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    val clipboard by lazy { getSystemService<ClipboardManager>() }

    private var _keyIDs = emptySet<String>()
    val keyIDs get() = _keyIDs

    private var serviceConnection: OpenPgpServiceConnection? = null
    var api: OpenPgpApi? = null

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        tag(TAG)

        _keyIDs = settings.getStringSet("openpgp_key_ids_set", null) ?: emptySet()
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.unbindFromService()
    }

    override fun onBound(service: IOpenPgpService2) {
        api = OpenPgpApi(this, service)
    }

    override fun onError(e: Exception) {
        e(e) { "Callers must handle their own exceptions" }
        throw e
    }

    fun bindToOpenKeychain(onBoundListener: OpenPgpServiceConnection.OnBound) {
        val providerPackageName = settings.getString("openpgp_provider_list", "")

        if (providerPackageName.isNullOrEmpty()) {
            Toast.makeText(this, resources.getString(R.string.provider_toast_text), Toast.LENGTH_LONG).show()
            val intent = Intent(this, UserPreference::class.java)
            startActivity(intent)
        } else {
            serviceConnection = OpenPgpServiceConnection(this, providerPackageName, onBoundListener)
            serviceConnection?.bindToService()

        }
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     * @param requestCode The code we'd like to use to identify the behaviour
     */
    fun handleUserInteractionRequest(result: Intent, requestCode: Int) {
        i { "RESULT_CODE_USER_INTERACTION_REQUIRED" }

        val pi: PendingIntent? = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)
        try {
            startIntentSenderFromChild(
                this, pi?.intentSender, requestCode,
                null, 0, 0, 0
            )
        } catch (e: IntentSender.SendIntentException) {
            e(e) { "SendIntentException" }
        }
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
     * Shows a [Snackbar] with the provided [message] and [length]
     */
    fun showSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        runOnUiThread { Snackbar.make(findViewById(android.R.id.content), message, length).show() }
    }

    /**
     * Base handling of OpenKeychain errors based on the error contained in [result]
     */
    fun handleError(result: Intent) {
        val error: OpenPgpError? = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)
        if (error != null) {
            when (error.errorId) {
                OpenPgpError.NO_OR_WRONG_PASSPHRASE -> {
                    showSnackbar(getString(R.string.openpgp_error_wrong_passphrase))
                }
                OpenPgpError.NO_USER_IDS -> {
                    showSnackbar(getString(R.string.openpgp_error_no_user_ids))
                }
                else -> {
                    showSnackbar(getString(R.string.openpgp_error_unknown, error.message))
                    e { "onError getErrorId: ${error.errorId}" }
                    e { "onError getMessage: ${error.message}" }
                }
            }
        }
    }

    companion object {
        private const val TAG = "APS/BasePgpActivity"
        const val REQUEST_DECRYPT = 101
        const val REQUEST_KEY_ID = 102

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
         * Gets the name of the password (excluding .gpg)
         */
        fun getName(fullPath: String): String {
            return FilenameUtils.getBaseName(fullPath)
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

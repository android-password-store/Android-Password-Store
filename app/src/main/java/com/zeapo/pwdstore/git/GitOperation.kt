/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.InteractivePasswordFinder
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.git.config.SshAuthData
import com.zeapo.pwdstore.git.config.SshjSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.requestInputFocusOnView
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


private class GitOperationPasswordFinder(val callingActivity: Activity, val passwordPref: String,
                                         @StringRes val message: Int) : InteractivePasswordFinder() {

    val encryptedSettings = callingActivity.getEncryptedPrefs("git_operation")

    override fun askForPassword(cont: Continuation<String?>, isRetry: Boolean) {
        val storedPassphrase = encryptedSettings.getString(passwordPref, null)
        if (isRetry)
            encryptedSettings.edit { putString(passwordPref, null) }
        if (storedPassphrase.isNullOrEmpty()) {
            val layoutInflater = LayoutInflater.from(callingActivity)

            @SuppressLint("InflateParams")
            val dialogView = layoutInflater.inflate(R.layout.git_passphrase_layout, null)
            val editPassphrase = dialogView.findViewById<TextInputEditText>(R.id.git_auth_passphrase)
            val rememberPassphrase = dialogView.findViewById<MaterialCheckBox>(R.id.git_auth_remember_passphrase)
            if (isRetry)
                editPassphrase.error = callingActivity.resources.getString(R.string.git_operation_wrong_passphrase)
            MaterialAlertDialogBuilder(callingActivity).run {
                setTitle(R.string.passphrase_dialog_title)
                setMessage(message)
                setView(dialogView)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val passphrase = editPassphrase.text.toString()
                    if (rememberPassphrase.isChecked) {
                        encryptedSettings.edit {
                            putString(passwordPref, passphrase)
                        }
                    }
                    cont.resume(passphrase)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    cont.resume(null)
                }
                setOnCancelListener {
                    cont.resume(null)
                }
                create()
            }.run {
                requestInputFocusOnView<TextInputEditText>(R.id.git_auth_passphrase)
                show()
            }
        } else {
            cont.resume(storedPassphrase)
        }
    }
}

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
abstract class GitOperation(fileDir: File, internal val callingActivity: Activity) {

    protected val repository: Repository? = PasswordRepository.getRepository(fileDir)
    internal var provider: CredentialsProvider? = null
    internal var command: GitCommand<*>? = null
    private val sshKeyFile = callingActivity.filesDir.resolve(".ssh_key")
    private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")

    private class PasswordFinderCredentialsProvider(private val username: String, private val passwordFinder: PasswordFinder) : CredentialsProvider() {

        override fun isInteractive() = true

        override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
            for (item in items) {
                when (item) {
                    is CredentialItem.Username -> item.value = username
                    is CredentialItem.Password -> item.value = passwordFinder.reqPassword(null)
                    else -> UnsupportedCredentialItem(uri, item.javaClass.name)
                }
            }
            return true
        }

        override fun supports(vararg items: CredentialItem) = items.all {
            it is CredentialItem.Username || it is CredentialItem.Password
        }
    }

    private fun withPasswordAuthentication(username: String, passwordFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(username, SshAuthData.Password(passwordFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = PasswordFinderCredentialsProvider(username, passwordFinder)
        return this
    }

    private fun withPublicKeyAuthentication(username: String, passphraseFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(username, SshAuthData.PublicKeyFile(sshKeyFile, passphraseFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = null
        return this
    }

    /**
     * Sets the authentication using OpenKeystore scheme
     *
     * @param identity The identiy to use
     * @return the current object
     */
    private fun setAuthentication(username: String, identity: SshApiSessionFactory.ApiIdentity?): GitOperation {
        SshSessionFactory.setInstance(SshApiSessionFactory(username, identity))
        this.provider = null
        return this
    }

    private fun getSshKey(make: Boolean) {
        try {
            // Ask the UserPreference to provide us with the ssh-key
            // onResult has to be handled by the callingActivity
            val intent = Intent(callingActivity.applicationContext, UserPreference::class.java)
            intent.putExtra("operation", if (make) "make_ssh_key" else "get_ssh_key")
            callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE)
        } catch (e: Exception) {
            println("Exception caught :(")
            e.printStackTrace()
        }
    }

    /**
     * Executes the GitCommand in an async task
     */
    abstract fun execute()

    /**
     * Executes the GitCommand in an async task after creating the authentication
     *
     * @param connectionMode the server-connection mode
     * @param username the username
     * @param identity the api identity to use for auth in OpenKeychain connection mode
     */
    fun executeAfterAuthentication(
        connectionMode: ConnectionMode,
        username: String,
        identity: SshApiSessionFactory.ApiIdentity?
    ) {
        when (connectionMode) {
            ConnectionMode.SshKey -> if (!sshKeyFile.exists()) {
                MaterialAlertDialogBuilder(callingActivity)
                    .setMessage(callingActivity.resources.getString(R.string.ssh_preferences_dialog_text))
                    .setTitle(callingActivity.resources.getString(R.string.ssh_preferences_dialog_title))
                    .setPositiveButton(callingActivity.resources.getString(R.string.ssh_preferences_dialog_import)) { _, _ ->
                        getSshKey(false)
                    }
                    .setNegativeButton(callingActivity.resources.getString(R.string.ssh_preferences_dialog_generate)) { _, _ ->
                        getSshKey(true)
                    }
                    .setNeutralButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
                        // Finish the blank GitActivity so user doesn't have to press back
                        callingActivity.finish()
                    }.show()
            } else {
                withPublicKeyAuthentication(username, GitOperationPasswordFinder(callingActivity,
                    "ssh_key_local_passphrase", R.string.passphrase_dialog_text)).execute()
            }
            ConnectionMode.OpenKeychain -> setAuthentication(username, identity).execute()
            ConnectionMode.Password -> withPasswordAuthentication(
                username, GitOperationPasswordFinder(callingActivity, "https_password",
                R.string.password_dialog_text)).execute()
            ConnectionMode.None -> execute()
        }
    }

    /**
     * Action to execute on error
     */
    open fun onError(err: Exception) {
        // Clear various auth related fields on failure
        when (SshSessionFactory.getInstance()) {
            is SshApiSessionFactory -> {
                PreferenceManager.getDefaultSharedPreferences(callingActivity.applicationContext)
                    .edit { putString("ssh_openkeystore_keyid", null) }
            }
            is SshjSessionFactory -> {
                callingActivity.applicationContext
                    .getEncryptedPrefs("git_operation")
                    .edit {
                        remove("ssh_key_local_passphrase")
                        remove("https_password")
                    }
            }
        }
    }

    /**
     * Action to execute on success
     */
    open fun onSuccess() {}

    companion object {
        const val GET_SSH_KEY_FROM_CLONE = 201
    }
}

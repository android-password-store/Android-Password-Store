/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.GitConfigSessionFactory
import com.zeapo.pwdstore.git.config.InteractivePasswordFinder
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.git.config.SshAuthData
import com.zeapo.pwdstore.git.config.SshjSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.requestInputFocusOnView
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import kotlin.coroutines.resume

/**
 * Creates a new git operation
 *
 * @param fileDir the git working tree directory
 * @param callingActivity the calling activity
 */
abstract class GitOperation(fileDir: File, internal val callingActivity: Activity) {

    protected val repository: Repository? = PasswordRepository.getRepository(fileDir)
    internal var provider: UsernamePasswordCredentialsProvider? = null
    internal var command: GitCommand<*>? = null
    private val sshKeyFile = callingActivity.filesDir.resolve(".ssh_key")
    private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")

    /**
     * Sets the authentication using user/pwd scheme
     *
     * @param username the username
     * @param password the password
     * @return the current object
     */
    internal open fun setAuthentication(username: String, password: String): GitOperation {
        SshSessionFactory.setInstance(GitConfigSessionFactory())
        this.provider = UsernamePasswordCredentialsProvider(username, password)
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
        val encryptedSettings = callingActivity.applicationContext.getEncryptedPrefs("git_operation")
        when (connectionMode) {
            ConnectionMode.SshKey -> {
                if (!sshKeyFile.exists()) {
                    MaterialAlertDialogBuilder(callingActivity)
                        .setMessage(callingActivity.resources.getString(R.string.ssh_preferences_dialog_text))
                        .setTitle(callingActivity.resources.getString(R.string.ssh_preferences_dialog_title))
                        .setPositiveButton(callingActivity.resources.getString(R.string.ssh_preferences_dialog_import)) { _, _ ->
                            try {
                                // Ask the UserPreference to provide us with the ssh-key
                                // onResult has to be handled by the callingActivity
                                val intent = Intent(callingActivity.applicationContext, UserPreference::class.java)
                                intent.putExtra("operation", "get_ssh_key")
                                callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE)
                            } catch (e: Exception) {
                                println("Exception caught :(")
                                e.printStackTrace()
                            }
                        }
                        .setNegativeButton(callingActivity.resources.getString(R.string.ssh_preferences_dialog_generate)) { _, _ ->
                            try {
                                // Duplicated code
                                val intent = Intent(callingActivity.applicationContext, UserPreference::class.java)
                                intent.putExtra("operation", "make_ssh_key")
                                callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE)
                            } catch (e: Exception) {
                                println("Exception caught :(")
                                e.printStackTrace()
                            }
                        }
                        .setNeutralButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
                            // Finish the blank GitActivity so user doesn't have to press back
                            callingActivity.finish()
                        }.show()
                } else {
                    withPublicKeyAuthentication(username, InteractivePasswordFinder { cont, isRetry ->
                        val storedPassphrase = encryptedSettings.getString("ssh_key_local_passphrase", null)
                        if (isRetry)
                            encryptedSettings.edit { putString("ssh_key_local_passphrase", null) }
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
                                setMessage(R.string.passphrase_dialog_text)
                                setView(dialogView)
                                setPositiveButton(R.string.dialog_ok) { _, _ ->
                                    val passphrase = editPassphrase.text.toString()
                                    if (rememberPassphrase.isChecked) {
                                        encryptedSettings.edit {
                                            putString("ssh_key_local_passphrase", passphrase)
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
                    }).execute()
                }
            }
            ConnectionMode.OpenKeychain -> {
                setAuthentication(username, identity).execute()
            }
            ConnectionMode.Password -> {
                @SuppressLint("InflateParams") val dialogView = callingActivity.layoutInflater.inflate(R.layout.git_passphrase_layout, null)
                val passwordView = dialogView.findViewById<TextInputEditText>(R.id.git_auth_passphrase)
                val password = encryptedSettings.getString("https_password", null)
                if (password != null && password.isNotEmpty()) {
                    setAuthentication(username, password).execute()
                } else {
                    val dialog = MaterialAlertDialogBuilder(callingActivity)
                        .setTitle(callingActivity.resources.getString(R.string.passphrase_dialog_title))
                        .setMessage(callingActivity.resources.getString(R.string.password_dialog_text))
                        .setView(dialogView)
                        .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                            if (dialogView.findViewById<MaterialCheckBox>(R.id.git_auth_remember_passphrase).isChecked) {
                                encryptedSettings.edit { putString("https_password", passwordView.text.toString()) }
                            }
                            // authenticate using the user/pwd and then execute the command
                            setAuthentication(username, passwordView.text.toString()).execute()
                        }
                        .setNegativeButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
                            callingActivity.finish()
                        }
                        .setOnCancelListener { callingActivity.finish() }
                        .create()
                    dialog.requestInputFocusOnView<TextInputEditText>(R.id.git_auth_passphrase)
                    dialog.show()
                }
            }
            ConnectionMode.None -> {
                execute()
            }
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
                    .edit { remove("ssh_key_local_passphrase") }
            }
            is GitConfigSessionFactory -> {
                callingActivity.applicationContext
                    .getEncryptedPrefs("git_operation")
                    .edit { remove("https_password") }
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

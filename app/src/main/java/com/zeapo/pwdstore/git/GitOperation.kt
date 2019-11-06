/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.GitConfigSessionFactory
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.git.config.SshConfigSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

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

    /**
     * Sets the authentication using ssh-key scheme
     *
     * @param sshKey the ssh-key file
     * @param username the username
     * @param passphrase the passphrase
     * @return the current object
     */
    internal open fun setAuthentication(sshKey: File, username: String, passphrase: String): GitOperation {
        val sessionFactory = SshConfigSessionFactory(sshKey.absolutePath, username, passphrase)
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
     * @param sshKey the ssh-key file to use in ssh-key connection mode
     * @param identity the api identity to use for auth in OpenKeychain connection mode
     */
    fun executeAfterAuthentication(
        connectionMode: String,
        username: String,
        sshKey: File?,
        identity: SshApiSessionFactory.ApiIdentity?
    ) {
        executeAfterAuthentication(connectionMode, username, sshKey, identity, false)
    }

    /**
     * Executes the GitCommand in an async task after creating the authentication
     *
     * @param connectionMode the server-connection mode
     * @param username the username
     * @param sshKey the ssh-key file to use in ssh-key connection mode
     * @param identity the api identity to use for auth in OpenKeychain connection mode
     * @param showError show the passphrase edit text in red
     */
    private fun executeAfterAuthentication(
        connectionMode: String,
        username: String,
        sshKey: File?,
        identity: SshApiSessionFactory.ApiIdentity?,
        showError: Boolean
    ) {
        if (connectionMode.equals("ssh-key", ignoreCase = true)) {
            if (sshKey == null || !sshKey.exists()) {
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
                val layoutInflater = LayoutInflater.from(callingActivity.applicationContext)
                @SuppressLint("InflateParams") val dialogView = layoutInflater.inflate(R.layout.git_passphrase_layout, null)
                val passphrase = dialogView.findViewById<EditText>(R.id.sshkey_passphrase)
                val settings = PreferenceManager.getDefaultSharedPreferences(callingActivity.applicationContext)
                val sshKeyPassphrase = settings.getString("ssh_key_passphrase", null)
                if (showError) {
                    passphrase.error = "Wrong passphrase"
                }
                val jsch = JSch()
                try {
                    val keyPair = KeyPair.load(jsch, callingActivity.filesDir.toString() + "/.ssh_key")

                    if (keyPair.isEncrypted) {
                        if (sshKeyPassphrase != null && sshKeyPassphrase.isNotEmpty()) {
                            if (keyPair.decrypt(sshKeyPassphrase)) {
                                // Authenticate using the ssh-key and then execute the command
                                setAuthentication(sshKey, username, sshKeyPassphrase).execute()
                            } else {
                                // call back the method
                                executeAfterAuthentication(connectionMode, username, sshKey, identity, true)
                            }
                        } else {
                            MaterialAlertDialogBuilder(callingActivity)
                                    .setTitle(callingActivity.resources.getString(R.string.passphrase_dialog_title))
                                    .setMessage(callingActivity.resources.getString(R.string.passphrase_dialog_text))
                                    .setView(dialogView)
                                    .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                                        if (keyPair.decrypt(passphrase.text.toString())) {
                                            val rememberPassphrase = (dialogView.findViewById<View>(R.id.sshkey_remember_passphrase) as CheckBox).isChecked
                                            if (rememberPassphrase) {
                                                settings.edit().putString("ssh_key_passphrase", passphrase.text.toString()).apply()
                                            }
                                            // Authenticate using the ssh-key and then execute the command
                                            setAuthentication(sshKey, username, passphrase.text.toString()).execute()
                                        } else {
                                            settings.edit().putString("ssh_key_passphrase", null).apply()
                                            // call back the method
                                            executeAfterAuthentication(connectionMode, username, sshKey, identity, true)
                                        }
                                    }.setNegativeButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
                                        // Do nothing.
                                    }.show()
                        }
                    } else {
                        setAuthentication(sshKey, username, "").execute()
                    }
                } catch (e: JSchException) {
                    e.printStackTrace()
                    MaterialAlertDialogBuilder(callingActivity)
                            .setTitle("Unable to open the ssh-key")
                            .setMessage("Please check that it was imported.")
                            .setPositiveButton("Ok") { _, _ -> callingActivity.finish() }
                            .show()
                }
            }
        } else if (connectionMode.equals("OpenKeychain", ignoreCase = true)) {
            setAuthentication(username, identity).execute()
        } else {
            val password = EditText(callingActivity)
            password.hint = "Password"
            password.width = LinearLayout.LayoutParams.MATCH_PARENT
            password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            MaterialAlertDialogBuilder(callingActivity)
                    .setTitle(callingActivity.resources.getString(R.string.passphrase_dialog_title))
                    .setMessage(callingActivity.resources.getString(R.string.password_dialog_text))
                    .setView(password)
                    .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                        // authenticate using the user/pwd and then execute the command
                        setAuthentication(username, password.text.toString()).execute()
                    }
                    .setNegativeButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
                        callingActivity.finish()
                    }
                    .show()
        }
    }

    /**
     * Action to execute on error
     */
    open fun onError(errorMessage: String) {
        if (SshSessionFactory.getInstance() is SshApiSessionFactory) {
            // Clear stored key id from settings on auth failure
            PreferenceManager.getDefaultSharedPreferences(callingActivity.applicationContext)
                    .edit().putString("ssh_openkeystore_keyid", null).apply()
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

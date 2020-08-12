/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import android.content.Intent
import androidx.annotation.CallSuper
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.d
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.ErrorMessages
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.config.InteractivePasswordFinder
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.git.config.SshAuthData
import com.zeapo.pwdstore.git.config.SshjSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import java.io.File
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish

/**
 * Creates a new git operation
 *
 * @param gitDir the git working tree directory
 * @param callingActivity the calling activity
 */
abstract class GitOperation(gitDir: File, internal val callingActivity: FragmentActivity) {

    abstract val commands: Array<GitCommand<out Any>>
    private var provider: CredentialsProvider? = null
    private val sshKeyFile = callingActivity.filesDir.resolve(".ssh_key")
    private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")
    protected var finishFromErrorDialog = true
    protected val repository = PasswordRepository.getRepository(gitDir)
    protected val git = Git(repository)
    protected val remoteBranch = GitSettings.branch

    private class PasswordFinderCredentialsProvider(private val passwordFinder: PasswordFinder) : CredentialsProvider() {

        override fun isInteractive() = true

        override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
            for (item in items) {
                when (item) {
                    is CredentialItem.Password -> item.value = passwordFinder.reqPassword(null)
                    else -> UnsupportedCredentialItem(uri, item.javaClass.name)
                }
            }
            return true
        }

        override fun supports(vararg items: CredentialItem) = items.all {
            it is CredentialItem.Password
        }
    }

    private fun withPasswordAuthentication(passwordFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(SshAuthData.Password(passwordFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = PasswordFinderCredentialsProvider(passwordFinder)
        return this
    }

    private fun withPublicKeyAuthentication(passphraseFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(SshAuthData.PublicKeyFile(sshKeyFile, passphraseFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = null
        return this
    }

    private fun withOpenKeychainAuthentication(identity: SshApiSessionFactory.ApiIdentity?): GitOperation {
        SshSessionFactory.setInstance(SshApiSessionFactory(identity))
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

    fun setCredentialProvider() {
        provider?.let { credentialsProvider ->
            commands.filterIsInstance<TransportCommand<*, *>>().forEach { it.setCredentialsProvider(credentialsProvider) }
        }
    }

    /**
     * Executes the GitCommand in an async task
     */
    abstract suspend fun execute()

    suspend fun executeAfterAuthentication(
        connectionMode: ConnectionMode,
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
                withPublicKeyAuthentication(
                    CredentialFinder(callingActivity, connectionMode)).execute()
            }
            ConnectionMode.OpenKeychain -> withOpenKeychainAuthentication(identity).execute()
            ConnectionMode.Password -> withPasswordAuthentication(
                CredentialFinder(callingActivity, connectionMode)).execute()
            ConnectionMode.None -> execute()
        }
    }

    /**
     * Action to execute on error
     */
    @CallSuper
    open fun onError(err: Exception) {
        // Clear various auth related fields on failure
        when (SshSessionFactory.getInstance()) {
            is SshApiSessionFactory -> {
                PreferenceManager.getDefaultSharedPreferences(callingActivity.applicationContext)
                    .edit { remove(PreferenceKeys.SSH_OPENKEYSTORE_KEYID) }
            }
            is SshjSessionFactory -> {
                callingActivity.getEncryptedPrefs("git_operation").edit {
                        remove(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE)
                        remove(PreferenceKeys.HTTPS_PASSWORD)
                    }
            }
        }
        d(err)
        MaterialAlertDialogBuilder(callingActivity)
            .setTitle(callingActivity.resources.getString(R.string.jgit_error_dialog_title))
            .setMessage(ErrorMessages[err])
            .setPositiveButton(callingActivity.resources.getString(R.string.dialog_ok)) { _, _ ->
                if (finishFromErrorDialog) callingActivity.finish()
            }.show()
    }

    /**
     * Action to execute on success
     */
    open fun onSuccess() {}

    companion object {

        const val GET_SSH_KEY_FROM_CLONE = 201
    }
}

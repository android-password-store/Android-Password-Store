/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import android.content.Intent
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.github.ajalt.timberkt.Timber.d
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.ErrorMessages
import com.zeapo.pwdstore.git.config.AuthMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.sshj.InteractivePasswordFinder
import com.zeapo.pwdstore.git.sshj.SshAuthData
import com.zeapo.pwdstore.git.sshj.SshKey
import com.zeapo.pwdstore.git.sshj.SshjSessionFactory
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.sharedPrefs
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish

const val ANDROID_KEYSTORE_ALIAS_SSH_KEY = "ssh_key"

/**
 * Creates a new git operation
 *
 * @param gitDir the git working tree directory
 * @param callingActivity the calling activity
 */
abstract class GitOperation(gitDir: File, internal val callingActivity: FragmentActivity) {

    abstract val commands: Array<GitCommand<out Any>>
    private var provider: CredentialsProvider? = null
    private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")
    protected var finishFromErrorDialog = true
    protected val repository = PasswordRepository.getRepository(gitDir)
    protected val git = Git(repository)
    protected val remoteBranch = GitSettings.branch

    private class HttpsCredentialsProvider(private val passwordFinder: PasswordFinder) : CredentialsProvider() {

        private var cachedPassword: CharArray? = null

        override fun isInteractive() = true

        override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
            for (item in items) {
                when (item) {
                    is CredentialItem.Username -> item.value = uri?.user
                    is CredentialItem.Password -> {
                        item.value = cachedPassword?.clone()
                            ?: passwordFinder.reqPassword(null).also {
                                cachedPassword = it.clone()
                            }
                    }
                    else -> UnsupportedCredentialItem(uri, item.javaClass.name)
                }
            }
            return true
        }

        override fun supports(vararg items: CredentialItem) = items.all {
            it is CredentialItem.Username || it is CredentialItem.Password
        }

        override fun reset(uri: URIish?) {
            cachedPassword?.fill(0.toChar())
            cachedPassword = null
        }
    }

    private fun withPasswordAuthentication(passwordFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(SshAuthData.Password(passwordFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = HttpsCredentialsProvider(passwordFinder)
        return this
    }

    private fun withSshKeyAuthentication(passphraseFinder: InteractivePasswordFinder): GitOperation {
        val sessionFactory = SshjSessionFactory(SshAuthData.SshKey(passphraseFinder), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
        this.provider = null
        return this
    }

    private fun withOpenKeychainAuthentication(activity: FragmentActivity): GitOperation {
        val sessionFactory = SshjSessionFactory(SshAuthData.OpenKeychain(activity), hostKeyFile)
        SshSessionFactory.setInstance(sessionFactory)
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

    private fun onMissingSshKeyFile() {
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
    }

    suspend fun executeAfterAuthentication(
        authMode: AuthMode,
    ) {
        when (authMode) {
            AuthMode.SshKey -> if (SshKey.exists) {
                if (SshKey.mustAuthenticate) {
                    val result = withContext(Dispatchers.Main) {
                        suspendCoroutine<BiometricAuthenticator.Result> { cont ->
                            BiometricAuthenticator.authenticate(callingActivity, R.string.biometric_prompt_title_ssh_auth) {
                                if (it !is BiometricAuthenticator.Result.Failure)
                                    cont.resume(it)
                            }
                        }
                    }
                    when (result) {
                        is BiometricAuthenticator.Result.Success -> {
                            withSshKeyAuthentication(CredentialFinder(callingActivity, authMode)).execute()
                        }
                        is BiometricAuthenticator.Result.Cancelled -> callingActivity.finish()
                        is BiometricAuthenticator.Result.Failure -> {
                            throw IllegalStateException("Biometric authentication failures should be ignored")
                        }
                        else -> {
                            // There is a chance we succeed if the user recently confirmed
                            // their screen lock. Doing so would have a potential to confuse
                            // users though, who might deduce that the screen lock
                            // protection is not effective. Hence, we fail with an error.
                            Toast.makeText(callingActivity.applicationContext, R.string.biometric_auth_generic_failure, Toast.LENGTH_LONG).show()
                            callingActivity.finish()
                        }
                    }
                } else {
                    withSshKeyAuthentication(CredentialFinder(callingActivity, authMode)).execute()
                }
            } else {
                onMissingSshKeyFile()
            }
            AuthMode.OpenKeychain -> withOpenKeychainAuthentication(callingActivity).execute()
            AuthMode.Password -> withPasswordAuthentication(
                CredentialFinder(callingActivity, authMode)).execute()
            AuthMode.None -> execute()
        }
    }

    /**
     * Action to execute on error
     */
    @CallSuper
    open fun onError(err: Exception) {
        // Clear various auth related fields on failure
        callingActivity.getEncryptedPrefs("git_operation").edit {
            remove(PreferenceKeys.HTTPS_PASSWORD)
        }
        callingActivity.sharedPrefs.edit { remove(PreferenceKeys.SSH_OPENKEYSTORE_KEYID) }
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

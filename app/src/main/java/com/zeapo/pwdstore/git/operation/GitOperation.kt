/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.operation

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.GitCommandExecutor
import com.zeapo.pwdstore.git.config.AuthMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.sshj.ContinuationContainerActivity
import com.zeapo.pwdstore.git.sshj.SshAuthMethod
import com.zeapo.pwdstore.git.sshj.SshKey
import com.zeapo.pwdstore.git.sshj.SshjSessionFactory
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.URIish

/**
 * Creates a new git operation
 *
 * @param callingActivity the calling activity
 */
abstract class GitOperation(protected val callingActivity: FragmentActivity) {

    abstract val commands: Array<GitCommand<out Any>>
    private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")
    private var sshSessionFactory: SshjSessionFactory? = null

    protected val repository = PasswordRepository.getRepository(null)!!
    protected val git = Git(repository)
    protected val remoteBranch = GitSettings.branch
    private val authActivity get() = callingActivity as ContinuationContainerActivity

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

    private fun getSshKey(make: Boolean) {
        runCatching {
            // Ask the UserPreference to provide us with the ssh-key
            val intent = Intent(callingActivity.applicationContext, UserPreference::class.java)
            intent.putExtra("operation", if (make) "make_ssh_key" else "get_ssh_key")
            callingActivity.startActivity(intent)
        }.onFailure { e ->
            e(e)
        }
    }

    private fun registerAuthProviders(authMethod: SshAuthMethod, credentialsProvider: CredentialsProvider? = null) {
        sshSessionFactory = SshjSessionFactory(authMethod, hostKeyFile)
        commands.filterIsInstance<TransportCommand<*, *>>().forEach { command ->
            command.setTransportConfigCallback { transport: Transport ->
                (transport as? SshTransport)?.sshSessionFactory = sshSessionFactory
                credentialsProvider?.let { transport.credentialsProvider = it }
            }
            command.setTimeout(CONNECT_TIMEOUT)
        }
    }

    /**
     * Executes the GitCommand in an async task.
     */
    suspend fun execute(): Result<Unit, Throwable> {
        if (!preExecute()) {
            return Ok(Unit)
        }
        val operationResult = GitCommandExecutor(
            callingActivity,
            this,
        ).execute()
        postExecute()
        return operationResult
    }

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

    suspend fun executeAfterAuthentication(authMode: AuthMode): Result<Unit, Throwable> {
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
                            registerAuthProviders(SshAuthMethod.SshKey(authActivity))
                        }
                        is BiometricAuthenticator.Result.Cancelled -> {
                            return Err(SSHException(DisconnectReason.AUTH_CANCELLED_BY_USER))
                        }
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
                    registerAuthProviders(SshAuthMethod.SshKey(authActivity))
                }
            } else {
                onMissingSshKeyFile()
                // This would correctly cancel the operation but won't surface a user-visible
                // error, allowing users to make the SSH key selection.
                return Err(SSHException(DisconnectReason.AUTH_CANCELLED_BY_USER))
            }
            AuthMode.OpenKeychain -> registerAuthProviders(SshAuthMethod.OpenKeychain(authActivity))
            AuthMode.Password -> {
                val httpsCredentialProvider = HttpsCredentialsProvider(CredentialFinder(callingActivity, AuthMode.Password))
                registerAuthProviders(SshAuthMethod.Password(authActivity), httpsCredentialProvider)
            }
            AuthMode.None -> {
            }
        }
        return execute()
    }

    /**
     * Called before execution of the Git operation.
     * Return false to cancel.
     */
    open fun preExecute() = true

    private suspend fun postExecute() {
        withContext(Dispatchers.IO) {
            sshSessionFactory?.close()
        }
    }

    companion object {

        /**
         * Timeout in seconds before [TransportCommand] will abort a stalled IO operation.
         */
        private const val CONNECT_TIMEOUT = 10
    }
}

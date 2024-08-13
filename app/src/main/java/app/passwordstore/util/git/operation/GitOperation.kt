/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.git.operation

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.sshkeygen.SshKeyGenActivity
import app.passwordstore.ui.sshkeygen.SshKeyImportActivity
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.auth.BiometricAuthenticator.Result.CanceledBySystem
import app.passwordstore.util.auth.BiometricAuthenticator.Result.CanceledByUser
import app.passwordstore.util.auth.BiometricAuthenticator.Result.Failure
import app.passwordstore.util.auth.BiometricAuthenticator.Result.Retry
import app.passwordstore.util.auth.BiometricAuthenticator.Result.Success
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.launchActivity
import app.passwordstore.util.git.GitCommandExecutor
import app.passwordstore.util.git.sshj.SshAuthMethod
import app.passwordstore.util.git.sshj.SshKey
import app.passwordstore.util.git.sshj.SshjSessionFactory
import app.passwordstore.util.settings.AuthMode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
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

  /** List of [GitCommand]s that are executed by an operation. */
  abstract val commands: Array<GitCommand<out Any>>

  /** Whether the operation requires authentication or not. */
  open val requiresAuth: Boolean = true
  private val hostKeyFile = callingActivity.filesDir.resolve(".host_key")
  private var sshSessionFactory: SshjSessionFactory? = null
  private val hiltEntryPoint =
    EntryPointAccessors.fromApplication<GitOperationEntryPoint>(callingActivity)
  protected val repository = PasswordRepository.repository!!
  protected val git = Git(repository)
  private val authActivity
    get() = callingActivity as AppCompatActivity

  private class HttpsCredentialsProvider(private val passwordFinder: PasswordFinder) :
    CredentialsProvider() {

    private var cachedPassword: CharArray? = null

    override fun isInteractive() = true

    override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
      for (item in items) {
        when (item) {
          is CredentialItem.Username -> item.value = uri?.user
          is CredentialItem.Password -> {
            item.value =
              cachedPassword?.clone()
                ?: passwordFinder.reqPassword(null).also { cachedPassword = it.clone() }
          }
          else -> UnsupportedCredentialItem(uri, item.javaClass.name)
        }
      }
      return true
    }

    override fun supports(vararg items: CredentialItem) =
      items.all { it is CredentialItem.Username || it is CredentialItem.Password }

    override fun reset(uri: URIish?) {
      cachedPassword?.fill(0.toChar())
      cachedPassword = null
    }
  }

  private fun getSshKey(generateKey: Boolean) {
    runCatching {
        callingActivity.launchActivity(
          if (generateKey) SshKeyGenActivity::class.java else SshKeyImportActivity::class.java
        )
      }
      .onFailure { e -> logcat(ERROR) { e.asLog() } }
  }

  private fun registerAuthProviders(
    authMethod: SshAuthMethod,
    credentialsProvider: CredentialsProvider? = null,
  ) {
    sshSessionFactory =
      SshjSessionFactory(authMethod, hostKeyFile, hiltEntryPoint.dispatcherProvider())
    commands.filterIsInstance<TransportCommand<*, *>>().forEach { command ->
      command.setTransportConfigCallback { transport: Transport ->
        (transport as? SshTransport)?.sshSessionFactory = sshSessionFactory
        credentialsProvider?.let { transport.credentialsProvider = it }
      }
      command.setTimeout(CONNECT_TIMEOUT)
    }
  }

  /** Executes the GitCommand in an async task. */
  suspend fun execute(): Result<Unit, Throwable> {
    if (!preExecute()) {
      return Ok(Unit)
    }
    val operationResult = GitCommandExecutor(callingActivity, this).execute()
    postExecute()
    return operationResult
  }

  private fun onMissingSshKeyFile() {
    MaterialAlertDialogBuilder(callingActivity)
      .setMessage(callingActivity.resources.getString(R.string.ssh_preferences_dialog_text))
      .setTitle(callingActivity.resources.getString(R.string.ssh_preferences_dialog_title))
      .setPositiveButton(callingActivity.resources.getString(R.string.button_label_import)) { _, _
        ->
        getSshKey(false)
      }
      .setNegativeButton(
        callingActivity.resources.getString(R.string.ssh_preferences_dialog_generate)
      ) { _, _ ->
        getSshKey(true)
      }
      .setNeutralButton(callingActivity.resources.getString(R.string.dialog_cancel)) { _, _ ->
        // Finish the blank GitActivity so user doesn't have to press back
        callingActivity.finish()
      }
      .show()
  }

  suspend fun executeAfterAuthentication(authMode: AuthMode): Result<Unit, Throwable> {
    when (authMode) {
      AuthMode.SshKey ->
        if (SshKey.exists) {
          if (SshKey.mustAuthenticate) {
            val result =
              withContext(hiltEntryPoint.dispatcherProvider().main()) {
                suspendCoroutine { cont ->
                  BiometricAuthenticator.authenticate(
                    callingActivity,
                    R.string.biometric_prompt_title_ssh_auth,
                  ) { result ->
                    if (result !is Failure && result !is Retry) cont.resume(result)
                  }
                }
              }
            when (result) {
              is Success -> {
                registerAuthProviders(SshAuthMethod.SshKey(authActivity))
              }
              is CanceledByUser -> {
                return Err(SSHException(DisconnectReason.AUTH_CANCELLED_BY_USER))
              }
              is Failure,
              is CanceledBySystem -> {
                throw IllegalStateException("Biometric authentication failures should be ignored")
              }
              else -> {
                // There is a chance we succeed if the user recently confirmed
                // their screen lock. Doing so would have a potential to confuse
                // users though, who might deduce that the screen lock
                // protection is not effective. Hence, we fail with an error.
                Toast.makeText(
                    callingActivity,
                    R.string.biometric_auth_generic_failure,
                    Toast.LENGTH_LONG,
                  )
                  .show()
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
      AuthMode.Password -> {
        val httpsCredentialProvider =
          HttpsCredentialsProvider(
            CredentialFinder(
              callingActivity,
              AuthMode.Password,
              hiltEntryPoint.dispatcherProvider(),
            )
          )
        registerAuthProviders(SshAuthMethod.Password(authActivity), httpsCredentialProvider)
      }
      AuthMode.None -> {}
    }
    return execute()
  }

  /** Called before execution of the Git operation. Return false to cancel. */
  open fun preExecute() = true

  private suspend fun postExecute() {
    withContext(hiltEntryPoint.dispatcherProvider().io()) { sshSessionFactory?.close() }
  }

  companion object {

    /** Timeout in seconds before [TransportCommand] will abort a stalled IO operation. */
    private const val CONNECT_TIMEOUT = 10
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface GitOperationEntryPoint {
    fun dispatcherProvider(): DispatcherProvider
  }
}

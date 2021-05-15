/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.sshj

import android.util.Base64
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.w
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.util.git.operation.CredentialFinder
import dev.msfjarvis.aps.util.settings.AuthMode
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer.PlainBuffer
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteSession
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS

sealed class SshAuthMethod(val activity: ContinuationContainerActivity) {
  class Password(activity: ContinuationContainerActivity) : SshAuthMethod(activity)
  class SshKey(activity: ContinuationContainerActivity) : SshAuthMethod(activity)
  class OpenKeychain(activity: ContinuationContainerActivity) : SshAuthMethod(activity)
}

abstract class InteractivePasswordFinder : PasswordFinder {

  private var isRetry = false

  abstract fun askForPassword(cont: Continuation<String?>, isRetry: Boolean)

  final override fun reqPassword(resource: Resource<*>?): CharArray {
    val password =
      runBlocking(Dispatchers.Main) {
        suspendCoroutine<String?> { cont -> askForPassword(cont, isRetry) }
      }
    isRetry = true
    return password?.toCharArray() ?: throw SSHException(DisconnectReason.AUTH_CANCELLED_BY_USER)
  }

  final override fun shouldRetry(resource: Resource<*>?) = true
}

class SshjSessionFactory(private val authMethod: SshAuthMethod, private val hostKeyFile: File) :
  SshSessionFactory() {

  private var currentSession: SshjSession? = null

  override fun getSession(
    uri: URIish,
    credentialsProvider: CredentialsProvider?,
    fs: FS?,
    tms: Int
  ): RemoteSession {
    return currentSession
      ?: SshjSession(uri, uri.user, authMethod, hostKeyFile).connect().also {
        d { "New SSH connection created" }
        currentSession = it
      }
  }

  fun close() {
    currentSession?.close()
  }
}

private fun makeTofuHostKeyVerifier(hostKeyFile: File): HostKeyVerifier {
  if (!hostKeyFile.exists()) {
    return HostKeyVerifier { _, _, key ->
      val digest =
        runCatching { SecurityUtils.getMessageDigest("SHA-256") }.getOrElse { e ->
          throw SSHRuntimeException(e)
        }
      digest.update(PlainBuffer().putPublicKey(key).compactData)
      val digestData = digest.digest()
      val hostKeyEntry = "SHA256:${Base64.encodeToString(digestData, Base64.NO_WRAP)}"
      d { "Trusting host key on first use: $hostKeyEntry" }
      hostKeyFile.writeText(hostKeyEntry)
      true
    }
  } else {
    val hostKeyEntry = hostKeyFile.readText()
    d { "Pinned host key: $hostKeyEntry" }
    return FingerprintVerifier.getInstance(hostKeyEntry)
  }
}

private class SshjSession(
  uri: URIish,
  private val username: String,
  private val authMethod: SshAuthMethod,
  private val hostKeyFile: File
) : RemoteSession {

  private lateinit var ssh: SSHClient
  private var currentCommand: Session? = null

  private val uri =
    if (uri.host.contains('@')) {
      // URIish's String constructor cannot handle '@' in the user part of the URI and the URL
      // constructor can't be used since Java's URL does not recognize the ssh scheme. We thus
      // need to patch everything up ourselves.
      d { "Before fixup: user=${uri.user}, host=${uri.host}" }
      val userPlusHost = "${uri.user}@${uri.host}"
      val realUser = userPlusHost.substringBeforeLast('@')
      val realHost = userPlusHost.substringAfterLast('@')
      uri.setUser(realUser).setHost(realHost).also {
        d { "After fixup: user=${it.user}, host=${it.host}" }
      }
    } else {
      uri
    }

  fun connect(): SshjSession {
    ssh = SSHClient(SshjConfig())
    ssh.addHostKeyVerifier(makeTofuHostKeyVerifier(hostKeyFile))
    ssh.connect(uri.host, uri.port.takeUnless { it == -1 } ?: 22)
    if (!ssh.isConnected) throw IOException()
    val passwordAuth = AuthPassword(CredentialFinder(authMethod.activity, AuthMode.Password))
    when (authMethod) {
      is SshAuthMethod.Password -> {
        ssh.auth(username, passwordAuth)
      }
      is SshAuthMethod.SshKey -> {
        val pubkeyAuth =
          AuthPublickey(SshKey.provide(ssh, CredentialFinder(authMethod.activity, AuthMode.SshKey)))
        ssh.auth(username, pubkeyAuth, passwordAuth)
      }
      is SshAuthMethod.OpenKeychain -> {
        runBlocking {
          OpenKeychainKeyProvider.prepareAndUse(authMethod.activity) { provider ->
            val openKeychainAuth = AuthPublickey(provider)
            ssh.auth(username, openKeychainAuth, passwordAuth)
          }
        }
      }
    }
    return this
  }

  override fun exec(commandName: String?, timeout: Int): Process {
    if (currentCommand != null) {
      w { "Killing old command" }
      disconnect()
    }
    val session = ssh.startSession()
    currentCommand = session
    return SshjProcess(session.exec(commandName), timeout.toLong())
  }

  /**
   * Kills the current command if one is running and returns the session into a state where `exec`
   * can be called.
   *
   * Note that this does *not* disconnect the session. Unfortunately, the function has to be called
   * `disconnect` to override the corresponding abstract function in `RemoteSession`.
   */
  override fun disconnect() {
    currentCommand?.close()
    currentCommand = null
  }

  fun close() {
    disconnect()
    ssh.close()
  }
}

private class SshjProcess(private val command: Session.Command, private val timeout: Long) :
  Process() {

  override fun waitFor(): Int {
    command.join(timeout, TimeUnit.SECONDS)
    command.close()
    return exitValue()
  }

  override fun destroy() = command.close()

  override fun getOutputStream(): OutputStream = command.outputStream

  override fun getErrorStream(): InputStream = command.errorStream

  override fun exitValue(): Int = command.exitStatus

  override fun getInputStream(): InputStream = command.inputStream
}

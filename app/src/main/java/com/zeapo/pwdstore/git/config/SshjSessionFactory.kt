/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import android.util.Base64
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.w
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer.PlainBuffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteSession
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

sealed class SshAuthData {
    class AndroidKeystoreKey(val keyAlias: String) : SshAuthData()
    class Password(val passwordFinder: InteractivePasswordFinder) : SshAuthData()
    class PublicKeyFile(val keyFile: File, val passphraseFinder: InteractivePasswordFinder) : SshAuthData()
}

abstract class InteractivePasswordFinder : PasswordFinder {

    private var isRetry = false
    private var shouldRetry = true

    abstract fun askForPassword(cont: Continuation<String?>, isRetry: Boolean)

    final override fun reqPassword(resource: Resource<*>?): CharArray {
        val password = runBlocking(Dispatchers.Main) {
            suspendCoroutine<String?> { cont ->
                askForPassword(cont, isRetry)
            }
        }
        isRetry = true
        return if (password != null) {
            password.toCharArray()
        } else {
            shouldRetry = false
            CharArray(0)
        }
    }

    final override fun shouldRetry(resource: Resource<*>?) = shouldRetry
}

class SshjSessionFactory(private val username: String, private val authData: SshAuthData, private val hostKeyFile: File) : SshSessionFactory() {

    override fun getSession(uri: URIish, credentialsProvider: CredentialsProvider?, fs: FS?, tms: Int): RemoteSession {
        return SshjSession(uri, username, authData, hostKeyFile).connect()
    }
}

private fun makeTofuHostKeyVerifier(hostKeyFile: File): HostKeyVerifier {
    if (!hostKeyFile.exists()) {
        return HostKeyVerifier { _, _, key ->
            val digest = try {
                SecurityUtils.getMessageDigest("SHA-256")
            } catch (e: GeneralSecurityException) {
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

private class SshjSession(private val uri: URIish, private val username: String, private val authData: SshAuthData, private val hostKeyFile: File) : RemoteSession {

    private lateinit var ssh: SSHClient
    private var currentCommand: Session? = null

    fun connect(): SshjSession {
        /*
         * The SSHJ default factories explicitly specify the KeyStore provider as 'BC'. We replace
         * them with factories that produce signature objects that choose the provider based on the
         * key type provided to them in initVerify().
         */
        val config = DefaultConfig().apply {
            val toPatch = mapOf(
                KeyType.RSA.toString() to AndroidKeystoreCompatRsaSignatureFactory,
                KeyType.ECDSA256.toString() to AndroidKeystoreCompatEcdsaSignatureFactory(KeyType.ECDSA256),
                KeyType.ECDSA384.toString() to AndroidKeystoreCompatEcdsaSignatureFactory(KeyType.ECDSA384),
                KeyType.ECDSA521.toString() to AndroidKeystoreCompatEcdsaSignatureFactory(KeyType.ECDSA521)
            )
            for ((name, factory) in toPatch) {
                val index = signatureFactories.indexOfFirst { it.name == name }
                if (index == -1)
                    continue
                signatureFactories.set(index, factory)
            }
        }
        ssh = SSHClient(config)
        ssh.addHostKeyVerifier(makeTofuHostKeyVerifier(hostKeyFile))
        ssh.connect(uri.host, uri.port.takeUnless { it == -1 } ?: 22)
        if (!ssh.isConnected)
            throw IOException()
        when (authData) {
            is SshAuthData.AndroidKeystoreKey -> {
                ssh.authPublickey(username, AndroidKeystoreKeyProvider(authData.keyAlias))
                d { "Authenticated with an Android Keystore key" }
            }
            is SshAuthData.Password -> {
                ssh.authPassword(username, authData.passwordFinder)
                d { "Authenticated with a password" }
            }
            is SshAuthData.PublicKeyFile -> {
                ssh.authPublickey(username, ssh.loadKeys(authData.keyFile.absolutePath, authData.passphraseFinder))
                d { "Authenticated with an SSH key file" }
            }
        }
        return this
    }

    override fun exec(commandName: String?, timeout: Int): Process {
        if (currentCommand != null) {
            w { "Killing old session" }
            currentCommand?.close()
            currentCommand = null
        }
        val session = ssh.startSession()
        currentCommand = session
        return SshjProcess(session.exec(commandName), timeout.toLong())
    }

    override fun disconnect() {
        currentCommand?.close()
        ssh.close()
    }
}

private class SshjProcess(private val command: Session.Command, private val timeout: Long) : Process() {

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

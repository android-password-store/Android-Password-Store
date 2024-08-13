/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.git.sshj

import app.passwordstore.util.log.LogcatLogger
import com.github.michaelbull.result.runCatching
import com.hierynomus.sshj.key.KeyAlgorithms
import com.hierynomus.sshj.transport.cipher.BlockCiphers
import com.hierynomus.sshj.transport.cipher.GcmCiphers
import com.hierynomus.sshj.transport.kex.ExtInfoClientFactory
import com.hierynomus.sshj.transport.mac.Macs
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import java.security.Security
import logcat.LogPriority.VERBOSE
import logcat.logcat
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.ConfigImpl
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.transport.kex.Curve25519SHA256
import net.schmizz.sshj.transport.kex.Curve25519SHA256.FactoryLibSsh
import net.schmizz.sshj.transport.kex.DHGexSHA256
import net.schmizz.sshj.transport.kex.ECDHNistP
import net.schmizz.sshj.transport.random.JCERandom
import net.schmizz.sshj.transport.random.SingletonRandomFactory
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger

fun setUpBouncyCastleForSshj() {
  // Replace the Android BC provider with the Java BouncyCastle provider since the former does
  // not include all the required algorithms.
  // Note: This may affect crypto operations in other parts of the application.
  val bcIndex =
    Security.getProviders().indexOfFirst { it.name == BouncyCastleProvider.PROVIDER_NAME }
  if (bcIndex == -1) {
    // No Android BC found, install Java BC at lowest priority.
    Security.addProvider(BouncyCastleProvider())
  } else {
    // Replace Android BC with Java BC, inserted at the same position.
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    // May be needed on Android Pie+ as per https://stackoverflow.com/a/57897224/297261
    runCatching { Class.forName("sun.security.jca.Providers") }
    Security.insertProviderAt(BouncyCastleProvider(), bcIndex + 1)
  }
  logcat("setUpBouncyCastleForSshj", priority = VERBOSE) {
    "JCE providers: ${Security.getProviders().joinToString { "${it.name} (${it.version})" }}"
  }
  // Prevent sshj from forwarding all cryptographic operations to BC.
  SecurityUtils.setRegisterBouncyCastle(false)
  SecurityUtils.setSecurityProvider(null)
}

private object LogcatLoggerFactory : LoggerFactory {

  override fun getLogger(name: String): Logger {
    return LogcatLogger(name)
  }

  override fun getLogger(clazz: Class<*>): Logger {
    return LogcatLogger(clazz.name)
  }
}

class SshjConfig : ConfigImpl() {

  init {
    loggerFactory = LogcatLoggerFactory
    keepAliveProvider = KeepAliveProvider.HEARTBEAT
    version = "OpenSSH_8.2p1 Ubuntu-4ubuntu0.1"

    initKeyExchangeFactories()
    initKeyAlgorithms()
    initRandomFactory()
    initFileKeyProviderFactories()
    initCipherFactories()
    initCompressionFactories()
    initMACFactories()
  }

  private fun initKeyExchangeFactories() {
    keyExchangeFactories =
      listOf(
        Curve25519SHA256.Factory(),
        FactoryLibSsh(),
        ECDHNistP.Factory521(),
        ECDHNistP.Factory384(),
        ECDHNistP.Factory256(),
        DHGexSHA256.Factory(),
        // Sends "ext-info-c" with the list of key exchange algorithms. This is needed to
        // get
        // rsa-sha2-* key types to work with some servers (e.g. GitHub).
        ExtInfoClientFactory(),
      )
  }

  private fun initKeyAlgorithms() {
    keyAlgorithms =
      listOf(
        KeyAlgorithms.SSHRSACertV01(),
        KeyAlgorithms.EdDSA25519(),
        KeyAlgorithms.ECDSASHANistp521(),
        KeyAlgorithms.ECDSASHANistp384(),
        KeyAlgorithms.ECDSASHANistp256(),
        KeyAlgorithms.RSASHA512(),
        KeyAlgorithms.RSASHA256(),
        KeyAlgorithms.SSHRSA(),
      )
  }

  private fun initRandomFactory() {
    randomFactory = SingletonRandomFactory(JCERandom.Factory())
  }

  private fun initFileKeyProviderFactories() {
    fileKeyProviderFactories =
      listOf(
        OpenSSHKeyV1KeyFile.Factory(),
        PKCS8KeyFile.Factory(),
        OpenSSHKeyFile.Factory(),
        PuTTYKeyFile.Factory(),
      )
  }

  private fun initCipherFactories() {
    cipherFactories =
      listOf(
        GcmCiphers.AES128GCM(),
        GcmCiphers.AES256GCM(),
        BlockCiphers.AES256CTR(),
        BlockCiphers.AES192CTR(),
        BlockCiphers.AES128CTR(),
      )
  }

  private fun initMACFactories() {
    macFactories =
      listOf(Macs.HMACSHA2512Etm(), Macs.HMACSHA2256Etm(), Macs.HMACSHA2512(), Macs.HMACSHA2256())
  }

  private fun initCompressionFactories() {
    compressionFactories = listOf(NoneCompression.Factory())
  }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.sshj

import com.hierynomus.sshj.key.KeyAlgorithm
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.interfaces.ECKey
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.signature.Signature
import org.openintents.ssh.authentication.SshAuthenticationApi

interface OpenKeychainPrivateKey : PrivateKey, ECKey {

  suspend fun sign(challenge: ByteArray, hashAlgorithm: Int): ByteArray

  override fun getFormat() = null
  override fun getEncoded() = null
}

class OpenKeychainWrappedKeyAlgorithmFactory(private val factory: Factory.Named<KeyAlgorithm>) :
  Factory.Named<KeyAlgorithm> by factory {

  override fun create() = OpenKeychainWrappedKeyAlgorithm(factory.create())
}

class OpenKeychainWrappedKeyAlgorithm(private val keyAlgorithm: KeyAlgorithm) : KeyAlgorithm by keyAlgorithm {

  private val hashAlgorithm =
    when (keyAlgorithm.keyAlgorithm) {
      "rsa-sha2-512" -> SshAuthenticationApi.SHA512
      "rsa-sha2-256" -> SshAuthenticationApi.SHA256
      "ssh-rsa", "ssh-rsa-cert-v01@openssh.com" -> SshAuthenticationApi.SHA1
      // Other algorithms don't use this value, but it has to be valid.
      else -> SshAuthenticationApi.SHA512
    }

  override fun newSignature() = OpenKeychainWrappedSignature(keyAlgorithm.newSignature(), hashAlgorithm)
}

class OpenKeychainWrappedSignature(private val wrappedSignature: Signature, private val hashAlgorithm: Int) :
  Signature by wrappedSignature {

  private val data = ByteArrayOutputStream()

  private var bridgedPrivateKey: OpenKeychainPrivateKey? = null

  override fun initSign(prvkey: PrivateKey?) {
    if (prvkey is OpenKeychainPrivateKey) {
      bridgedPrivateKey = prvkey
    } else {
      wrappedSignature.initSign(prvkey)
    }
  }

  override fun update(H: ByteArray?) {
    if (bridgedPrivateKey != null) {
      data.write(H!!)
    } else {
      wrappedSignature.update(H)
    }
  }

  override fun update(H: ByteArray?, off: Int, len: Int) {
    if (bridgedPrivateKey != null) {
      data.write(H!!, off, len)
    } else {
      wrappedSignature.update(H, off, len)
    }
  }

  override fun sign(): ByteArray? =
    if (bridgedPrivateKey != null) {
      runBlocking { bridgedPrivateKey!!.sign(data.toByteArray(), hashAlgorithm) }
    } else {
      wrappedSignature.sign()
    }

  override fun encode(signature: ByteArray?): ByteArray? =
    if (bridgedPrivateKey != null) {
      require(signature != null) { "OpenKeychain signature must not be null" }
      val encodedSignature = Buffer.PlainBuffer(signature)
      // We need to drop the algorithm name and extract the raw signature since SSHJ adds the
      // name
      // later.
      encodedSignature.readString()
      encodedSignature.readBytes().also {
        bridgedPrivateKey = null
        data.reset()
      }
    } else {
      wrappedSignature.encode(signature)
    }
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.key.protection.PasswordBasedSecretKeyRingProtector
import org.pgpainless.util.Passphrase

public class PGPainlessCryptoHandler @Inject constructor() : CryptoHandler {

  public override fun decrypt(
    privateKey: String,
    password: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
  ) {
    val pgpSecretKeyRing = PGPainless.readKeyRing().secretKeyRing(privateKey)
    val keyringCollection = PGPSecretKeyRingCollection(listOf(pgpSecretKeyRing))
    val protector =
      PasswordBasedSecretKeyRingProtector.forKey(
        pgpSecretKeyRing,
        Passphrase.fromPassword(password)
      )
    PGPainless.decryptAndOrVerify()
      .onInputStream(ciphertextStream)
      .withOptions(
        ConsumerOptions()
          .addDecryptionKeys(keyringCollection, protector)
          .addDecryptionPassphrase(Passphrase.fromPassword(password))
      )
      .use { decryptionStream -> decryptionStream.copyTo(outputStream) }
  }

  public override fun encrypt(
    pubKeys: List<String>,
    plaintextStream: InputStream,
    outputStream: OutputStream,
  ) {
    val pubKeysStream = ByteArrayInputStream(pubKeys.joinToString("\n").toByteArray())
    val publicKeyRingCollection =
      pubKeysStream.use {
        ArmoredInputStream(it).use { armoredInputStream ->
          PGPainless.readKeyRing().publicKeyRingCollection(armoredInputStream)
        }
      }
    val encOpt = EncryptionOptions().apply { publicKeyRingCollection.forEach { addRecipient(it) } }
    val prodOpt = ProducerOptions.encrypt(encOpt).setAsciiArmor(true)
    PGPainless.encryptAndOrSign().onOutputStream(outputStream).withOptions(prodOpt).use {
      encryptionStream ->
      plaintextStream.copyTo(encryptionStream)
    }
  }

  public override fun canHandle(fileName: String): Boolean {
    return fileName.split('.').lastOrNull() == "gpg"
  }
}

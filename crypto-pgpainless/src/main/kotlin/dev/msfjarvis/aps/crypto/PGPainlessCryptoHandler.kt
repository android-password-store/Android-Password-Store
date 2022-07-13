/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.crypto.errors.CryptoHandlerException
import dev.msfjarvis.aps.crypto.errors.IncorrectPassphraseException
import dev.msfjarvis.aps.crypto.errors.NoKeysProvided
import dev.msfjarvis.aps.crypto.errors.UnknownError
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.exception.WrongPassphraseException
import org.pgpainless.key.protection.PasswordBasedSecretKeyRingProtector
import org.pgpainless.util.Passphrase

public class PGPainlessCryptoHandler @Inject constructor() : CryptoHandler<PGPKey> {

  public override fun decrypt(
    privateKey: PGPKey,
    passphrase: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
  ): Result<Unit, CryptoHandlerException> =
    runCatching {
        val pgpSecretKeyRing = PGPainless.readKeyRing().secretKeyRing(privateKey.contents)
        val keyringCollection = PGPSecretKeyRingCollection(listOf(pgpSecretKeyRing))
        val protector =
          PasswordBasedSecretKeyRingProtector.forKey(
            pgpSecretKeyRing,
            Passphrase.fromPassword(passphrase)
          )
        PGPainless.decryptAndOrVerify()
          .onInputStream(ciphertextStream)
          .withOptions(
            ConsumerOptions()
              .addDecryptionKeys(keyringCollection, protector)
              .addDecryptionPassphrase(Passphrase.fromPassword(passphrase))
          )
          .use { decryptionStream -> decryptionStream.copyTo(outputStream) }
        return@runCatching
      }
      .mapError { error ->
        when (error) {
          is WrongPassphraseException -> IncorrectPassphraseException(error)
          else -> UnknownError(error)
        }
      }

  public override fun encrypt(
    keys: List<PGPKey>,
    plaintextStream: InputStream,
    outputStream: OutputStream,
  ): Result<Unit, CryptoHandlerException> =
    runCatching {
        if (keys.isEmpty()) throw NoKeysProvided("No keys provided for encryption")
        val publicKeyRings = arrayListOf<PGPPublicKeyRing>()
        val armoredKeys =
          keys.joinToString("\n") { key -> key.contents.decodeToString() }.toByteArray()
        val secKeysStream = ByteArrayInputStream(armoredKeys)
        val secretKeyRingCollection =
          PGPainless.readKeyRing().secretKeyRingCollection(secKeysStream)
        secretKeyRingCollection.forEach { secretKeyRing ->
          publicKeyRings.add(PGPainless.extractCertificate(secretKeyRing))
        }
        if (publicKeyRings.isEmpty()) {
          val pubKeysStream = ByteArrayInputStream(armoredKeys)
          val publicKeyRingCollection =
            PGPainless.readKeyRing().publicKeyRingCollection(pubKeysStream)
          publicKeyRings.addAll(publicKeyRingCollection)
        }
        require(publicKeyRings.isNotEmpty()) { "No public keys to encrypt message to" }
        val publicKeyRingCollection = PGPPublicKeyRingCollection(publicKeyRings)
        val encryptionOptions = EncryptionOptions().addRecipients(publicKeyRingCollection)
        val producerOptions = ProducerOptions.encrypt(encryptionOptions).setAsciiArmor(false)
        val encryptor =
          PGPainless.encryptAndOrSign().onOutputStream(outputStream).withOptions(producerOptions)
        plaintextStream.copyTo(encryptor)
        encryptor.close()
        val result = encryptor.result
        publicKeyRingCollection.keyRings.forEach { keyRing ->
          require(result.isEncryptedFor(keyRing)) {
            "Stream should be encrypted for ${keyRing.publicKey.keyID} but wasn't"
          }
        }
      }
      .mapError { error ->
        when (error) {
          is CryptoHandlerException -> error
          else -> UnknownError(error)
        }
      }

  public override fun canHandle(fileName: String): Boolean {
    return fileName.split('.').lastOrNull() == "gpg"
  }
}

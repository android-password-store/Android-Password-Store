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
import dev.msfjarvis.aps.crypto.errors.UnknownError
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
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
        require(keys.isNotEmpty()) { "No keys provided for encryption" }
        val armoredKeys = keys.map { key -> key.contents.decodeToString() }
        val pubKeysStream = ByteArrayInputStream(armoredKeys.joinToString("\n").toByteArray())
        val publicKeyRingCollection =
          pubKeysStream.use { PGPainless.readKeyRing().publicKeyRingCollection(pubKeysStream) }
        val encryptionOptions =
          EncryptionOptions.encryptCommunications()
            .addRecipients(publicKeyRingCollection.asIterable())
        val producerOptions = ProducerOptions.encrypt(encryptionOptions).setAsciiArmor(true)
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
        return@runCatching
      }
      .mapError { error -> UnknownError(error) }

  public override fun canHandle(fileName: String): Boolean {
    return fileName.split('.').lastOrNull() == "gpg"
  }
}

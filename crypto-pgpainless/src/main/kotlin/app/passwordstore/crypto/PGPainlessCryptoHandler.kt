/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.errors.CryptoHandlerException
import app.passwordstore.crypto.errors.IncorrectPassphraseException
import app.passwordstore.crypto.errors.NoKeysProvided
import app.passwordstore.crypto.errors.UnknownError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import java.io.InputStream
import java.io.OutputStream
import org.bouncycastle.CachingPublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSessionKey
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.decryption_verification.HardwareSecurity
import org.pgpainless.decryption_verification.HardwareSecurity.HardwareDataDecryptorFactory
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.exception.WrongPassphraseException
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase

public class PGPainlessCryptoHandler : CryptoHandler<PGPKey, PGPEncryptedSessionKey, PGPSessionKey> {

  public override fun decrypt(
    keys: List<PGPKey>,
    passphrase: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
    onDecryptSessionKey: (PGPEncryptedSessionKey) -> PGPSessionKey
  ): Result<Unit, CryptoHandlerException> =
    runCatching {
        if (keys.isEmpty()) throw NoKeysProvided("No keys provided for encryption")
        val keyringCollection =
          keys
            .map { key -> PGPainless.readKeyRing().secretKeyRing(key.contents) }
            .run(::PGPSecretKeyRingCollection)
        val protector = SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(passphrase))
        val hardwareBackedKeys =
          keyringCollection.mapNotNull { keyring ->
            KeyUtils.tryGetEncryptionKey(keyring)
              ?.takeIf { it.keyID in HardwareSecurity.getIdsOfHardwareBackedKeys(keyring) }
          }
        PGPainless.decryptAndOrVerify()
          .onInputStream(ciphertextStream)
          .withOptions(
            ConsumerOptions().apply {
                for (key in hardwareBackedKeys) {
                  addCustomDecryptorFactory(
                    setOf(key.keyID),
                    CachingPublicKeyDataDecryptorFactory(
                      HardwareDataDecryptorFactory { keyAlgorithm, secKeyData ->
                        onDecryptSessionKey(
                          PGPEncryptedSessionKey(
                            key.publicKey,
                            PublicKeyAlgorithm.requireFromId(keyAlgorithm),
                            secKeyData
                          )
                        ).key
                      }
                    )
                  )
                }
                addDecryptionKeys(keyringCollection, protector)
                addDecryptionPassphrase(Passphrase.fromPassword(passphrase))
            }
          )
          .use { decryptionStream -> decryptionStream.copyTo(outputStream) }
        return@runCatching
      }
      .mapError { error ->
        when (error) {
          is CryptoHandlerException -> error
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
        val publicKeyRings =
          keys.mapNotNull(KeyUtils::tryParseKeyring).mapNotNull { keyRing ->
            when (keyRing) {
              is PGPPublicKeyRing -> keyRing
              is PGPSecretKeyRing -> PGPainless.extractCertificate(keyRing)
              else -> null
            }
          }
        require(keys.size == publicKeyRings.size) {
          "Failed to parse all keys: keys=${keys.size},parsed=${publicKeyRings.size}"
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

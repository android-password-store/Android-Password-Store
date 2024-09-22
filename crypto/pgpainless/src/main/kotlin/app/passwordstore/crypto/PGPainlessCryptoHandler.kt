/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.errors.CryptoHandlerException
import app.passwordstore.crypto.errors.IncorrectPassphraseException
import app.passwordstore.crypto.errors.NoKeysProvidedException
import app.passwordstore.crypto.errors.NonStandardAEAD
import app.passwordstore.crypto.errors.UnknownError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.util.io.Streams
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.exception.MessageNotIntegrityProtectedException
import org.pgpainless.exception.WrongPassphraseException
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase

public class PGPainlessCryptoHandler @Inject constructor() :
  CryptoHandler<PGPKey, PGPEncryptOptions, PGPDecryptOptions> {

  /**
   * Decrypts the given [ciphertextStream] using [PGPainless] and writes the decrypted output to
   * [outputStream]. The provided [passphrase] is wrapped in a [SecretKeyRingProtector] and the
   * [keys] argument is defensively checked to ensure it has at least one key present.
   *
   * @see CryptoHandler.decrypt
   */
  public override fun decrypt(
    keys: List<PGPKey>,
    passphrase: String,
    ciphertextStream: InputStream,
    outputStream: OutputStream,
    options: PGPDecryptOptions,
  ): Result<Unit, CryptoHandlerException> =
    runCatching {
        if (keys.isEmpty()) {
          throw NoKeysProvidedException
        }
        val keyringCollection =
          keys
            .mapNotNull { key -> PGPainless.readKeyRing().secretKeyRing(key.contents) }
            .run(::PGPSecretKeyRingCollection)
        val protector = SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(passphrase))
        val decryptionStream =
          PGPainless.decryptAndOrVerify()
            .onInputStream(ciphertextStream)
            .withOptions(
              ConsumerOptions()
                .addDecryptionKeys(keyringCollection, protector)
                .addDecryptionPassphrase(Passphrase.fromPassword(passphrase))
            )
        decryptionStream.use { Streams.pipeAll(it, outputStream) }
        return@runCatching
      }
      .mapError { error ->
        when (error) {
          is WrongPassphraseException -> IncorrectPassphraseException(error)
          is CryptoHandlerException -> error
          is MessageNotIntegrityProtectedException -> {
            if (error.message?.contains("Symmetrically Encrypted Data") == true) {
              NonStandardAEAD(error)
            } else {
              UnknownError(error)
            }
          }
          else -> UnknownError(error)
        }
      }

  /**
   * Encrypts the provided [plaintextStream] and writes the encrypted output to [outputStream]. The
   * [keys] argument is defensively checked to contain at least one key.
   *
   * @see CryptoHandler.encrypt
   */
  public override fun encrypt(
    keys: List<PGPKey>,
    plaintextStream: InputStream,
    outputStream: OutputStream,
    options: PGPEncryptOptions,
  ): Result<Unit, CryptoHandlerException> =
    runCatching {
        if (keys.isEmpty()) {
          throw NoKeysProvidedException
        }
        val publicKeyRings =
          keys.mapNotNull(KeyUtils::tryParseKeyring).mapNotNull { keyRing ->
            when (keyRing) {
              is PGPPublicKeyRing -> keyRing
              is PGPSecretKeyRing -> PGPainless.extractCertificate(keyRing)
              else -> null
            }
          }
        require(keys.size == publicKeyRings.size) {
          "Failed to parse all keys: ${keys.size} keys were provided but only ${publicKeyRings.size} were valid"
        }
        if (publicKeyRings.isEmpty()) {
          throw NoKeysProvidedException
        }
        val publicKeyRingCollection = PGPPublicKeyRingCollection(publicKeyRings)
        val encryptionOptions = EncryptionOptions().addRecipients(publicKeyRingCollection)
        val producerOptions =
          ProducerOptions.encrypt(encryptionOptions)
            .setAsciiArmor(options.isOptionEnabled(PGPEncryptOptions.ASCII_ARMOR))
        val encryptionStream =
          PGPainless.encryptAndOrSign().onOutputStream(outputStream).withOptions(producerOptions)
        encryptionStream.use { Streams.pipeAll(plaintextStream, it) }
        val result = encryptionStream.result
        publicKeyRingCollection.forEach { keyRing ->
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

  /** Runs a naive check on the extension for the given [fileName] to check if it is a PGP file. */
  public override fun canHandle(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "") == "gpg"
  }

  public override fun isPassphraseProtected(keys: List<PGPKey>): Boolean =
    keys
      .mapNotNull { key -> PGPainless.readKeyRing().secretKeyRing(key.contents) }
      .map(::keyringHasPassphrase)
      .all { it }

  internal fun keyringHasPassphrase(keyRing: PGPSecretKeyRing) =
    runCatching { keyRing.secretKey.extractPrivateKey(null) }
      .mapBoth(success = { false }, failure = { true })
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.errors.IncorrectPassphraseException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@Suppress("Unused") // Test runner handles it internally
enum class EncryptionKey(val keySet: List<PGPKey>) {
  PUBLIC(listOf(PGPKey(TestUtils.getArmoredPublicKey()))),
  SECRET(listOf(PGPKey(TestUtils.getArmoredSecretKey()))),
  ALL(listOf(PGPKey(TestUtils.getArmoredPublicKey()), PGPKey(TestUtils.getArmoredSecretKey()))),
}

@RunWith(TestParameterInjector::class)
class PGPainlessCryptoHandlerTest {

  @TestParameter private lateinit var encryptionKey: EncryptionKey
  private val cryptoHandler = PGPainlessCryptoHandler()
  private val secretKey = PGPKey(TestUtils.getArmoredSecretKey())

  @Test
  fun encryptAndDecrypt() {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
        ciphertextStream,
        PGPEncryptOptions.Builder().build(),
      )
    assertIs<Ok<Unit>>(encryptRes)
    val plaintextStream = ByteArrayOutputStream()
    val decryptRes =
      cryptoHandler.decrypt(
        listOf(secretKey),
        CryptoConstants.KEY_PASSPHRASE,
        ciphertextStream.toByteArray().inputStream(),
        plaintextStream,
        PGPDecryptOptions.Builder().build(),
      )
    assertIs<Ok<Unit>>(decryptRes)
    assertEquals(CryptoConstants.PLAIN_TEXT, plaintextStream.toString(Charsets.UTF_8))
  }

  @Test
  fun decryptWithWrongPassphrase() {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
        ciphertextStream,
        PGPEncryptOptions.Builder().build(),
      )
    assertIs<Ok<Unit>>(encryptRes)
    val plaintextStream = ByteArrayOutputStream()
    val result =
      cryptoHandler.decrypt(
        listOf(secretKey),
        "very incorrect passphrase",
        ciphertextStream.toByteArray().inputStream(),
        plaintextStream,
        PGPDecryptOptions.Builder().build(),
      )
    assertIs<Err<Throwable>>(result)
    assertIs<IncorrectPassphraseException>(result.getError())
  }

  @Test
  fun encryptAsciiArmored() {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
        ciphertextStream,
        PGPEncryptOptions.Builder().withAsciiArmor(true).build(),
      )
    assertIs<Ok<Unit>>(encryptRes)
    val ciphertext = ciphertextStream.toString(Charsets.UTF_8)
    assertContains(ciphertext, "Version: PGPainless")
    assertContains(ciphertext, "-----BEGIN PGP MESSAGE-----")
    assertContains(ciphertext, "-----END PGP MESSAGE-----")
  }

  @Test
  fun canHandleFiltersFormats() {
    assertFalse { cryptoHandler.canHandle("example.com") }
    assertTrue { cryptoHandler.canHandle("example.com.gpg") }
    assertFalse { cryptoHandler.canHandle("example.com.asc") }
  }
}

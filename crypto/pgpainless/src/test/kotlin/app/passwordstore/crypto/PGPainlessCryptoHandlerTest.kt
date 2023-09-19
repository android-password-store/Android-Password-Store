/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("JUnitMalformedDeclaration") // The test runner takes care of it

package app.passwordstore.crypto

import app.passwordstore.crypto.CryptoConstants.KEY_PASSPHRASE
import app.passwordstore.crypto.CryptoConstants.PLAIN_TEXT
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
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.MessageInspector

@Suppress("Unused") // Test runner handles it internally
enum class EncryptionKey(val keySet: List<PGPKey>) {
  PUBLIC(listOf(PGPKey(TestUtils.getArmoredPublicKey()))),
  SECRET(listOf(PGPKey(TestUtils.getArmoredSecretKey()))),
  ALL(listOf(PGPKey(TestUtils.getArmoredPublicKey()), PGPKey(TestUtils.getArmoredSecretKey()))),
}

@RunWith(TestParameterInjector::class)
class PGPainlessCryptoHandlerTest {

  private val cryptoHandler = PGPainlessCryptoHandler()
  private val secretKey = PGPKey(TestUtils.getArmoredSecretKey())

  @Test
  fun encryptAndDecrypt(@TestParameter encryptionKey: EncryptionKey) {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
        ciphertextStream,
        PGPEncryptOptions.Builder().build(),
      )
    assertIs<Ok<Unit>>(encryptRes)
    val plaintextStream = ByteArrayOutputStream()
    val decryptRes =
      cryptoHandler.decrypt(
        listOf(secretKey),
        KEY_PASSPHRASE,
        ciphertextStream.toByteArray().inputStream(),
        plaintextStream,
        PGPDecryptOptions.Builder().build(),
      )
    assertIs<Ok<Unit>>(decryptRes)
    assertEquals(PLAIN_TEXT, plaintextStream.toString(Charsets.UTF_8))
  }

  @Test
  fun decryptWithWrongPassphrase(@TestParameter encryptionKey: EncryptionKey) {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
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
  fun encryptAsciiArmored(@TestParameter encryptionKey: EncryptionKey) {
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        encryptionKey.keySet,
        PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
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
  fun encryptMultiple() {
    val alice =
      PGPainless.generateKeyRing().modernKeyRing("Alice <owner@example.com>", KEY_PASSPHRASE)
    val bob = PGPainless.generateKeyRing().modernKeyRing("Bob <owner@example.com>", KEY_PASSPHRASE)
    val aliceKey = PGPKey(PGPainless.asciiArmor(alice).encodeToByteArray())
    val bobKey = PGPKey(PGPainless.asciiArmor(bob).encodeToByteArray())
    val ciphertextStream = ByteArrayOutputStream()
    val encryptRes =
      cryptoHandler.encrypt(
        listOf(aliceKey, bobKey),
        PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
        ciphertextStream,
        PGPEncryptOptions.Builder().withAsciiArmor(true).build(),
      )
    assertIs<Ok<Unit>>(encryptRes)
    val message = ciphertextStream.toByteArray().decodeToString()
    val info = MessageInspector.determineEncryptionInfoForMessage(message)
    assertTrue(info.isEncrypted)
    assertEquals(2, info.keyIds.size)
    assertFalse(info.isSignedOnly)
    for (key in listOf(aliceKey, bobKey)) {
      val ciphertextStreamCopy = message.byteInputStream()
      val plaintextStream = ByteArrayOutputStream()
      val res =
        cryptoHandler.decrypt(
          listOf(key),
          KEY_PASSPHRASE,
          ciphertextStreamCopy,
          plaintextStream,
          PGPDecryptOptions.Builder().build(),
        )
      assertIs<Ok<Unit>>(res)
    }
  }

  @Test
  fun canHandleFiltersFormats() {
    assertFalse { cryptoHandler.canHandle("example.com") }
    assertTrue { cryptoHandler.canHandle("example.com.gpg") }
    assertFalse { cryptoHandler.canHandle("example.com.asc") }
  }
}

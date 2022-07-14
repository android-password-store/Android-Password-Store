/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.errors.IncorrectPassphraseException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getError
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@Suppress("Unused") // Test runner handles it internally
enum class EncryptionKey(val key: PGPKey) {
  PUBLIC(PGPKey(TestUtils.getArmoredPublicKey())),
  SECRET(PGPKey(TestUtils.getArmoredPrivateKey())),
}

@RunWith(TestParameterInjector::class)
class PGPainlessCryptoHandlerTest {

  @TestParameter private lateinit var encryptionKey: EncryptionKey
  private val cryptoHandler = PGPainlessCryptoHandler()
  private val privateKey = PGPKey(TestUtils.getArmoredPrivateKey())

  @Test
  fun encryptAndDecrypt() {
    val ciphertextStream = ByteArrayOutputStream()
    cryptoHandler.encrypt(
      listOf(encryptionKey.key),
      CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
      ciphertextStream,
    )
    val plaintextStream = ByteArrayOutputStream()
    cryptoHandler.decrypt(
      privateKey,
      CryptoConstants.KEY_PASSPHRASE,
      ciphertextStream.toByteArray().inputStream(),
      plaintextStream,
    )
    assertEquals(CryptoConstants.PLAIN_TEXT, plaintextStream.toString(Charsets.UTF_8))
  }

  @Test
  fun decryptWithWrongPassphrase() {
    val ciphertextStream = ByteArrayOutputStream()
    cryptoHandler.encrypt(
      listOf(encryptionKey.key),
      CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
      ciphertextStream,
    )
    val plaintextStream = ByteArrayOutputStream()
    val result =
      cryptoHandler.decrypt(
        privateKey,
        "very incorrect passphrase",
        ciphertextStream.toByteArray().inputStream(),
        plaintextStream,
      )
    assertIs<Err<Throwable>>(result)
    assertIs<IncorrectPassphraseException>(result.getError())
  }

  @Test
  fun canHandleFiltersFormats() {
    assertFalse { cryptoHandler.canHandle("example.com") }
    assertTrue { cryptoHandler.canHandle("example.com.gpg") }
    assertFalse { cryptoHandler.canHandle("example.com.asc") }
  }
}

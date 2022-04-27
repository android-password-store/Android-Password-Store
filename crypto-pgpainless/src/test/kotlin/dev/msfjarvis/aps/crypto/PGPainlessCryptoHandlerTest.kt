/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getError
import dev.msfjarvis.aps.crypto.errors.IncorrectPassphraseException
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PGPainlessCryptoHandlerTest {

  private val cryptoHandler = PGPainlessCryptoHandler()
  private val privateKey = PGPKey(TestUtils.getArmoredPrivateKey())
  private val publicKey = PGPKey(TestUtils.getArmoredPublicKey())

  @Test
  fun encryptAndDecrypt() {
    val ciphertextStream = ByteArrayOutputStream()
    cryptoHandler.encrypt(
      listOf(publicKey),
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
      listOf(publicKey),
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

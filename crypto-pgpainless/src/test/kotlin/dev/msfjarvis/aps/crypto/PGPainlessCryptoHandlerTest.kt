/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PGPainlessCryptoHandlerTest {

  private val cryptoHandler = PGPainlessCryptoHandler()

  @Test
  fun encrypt_and_decrypt() {
    val key = TestUtils.getArmoredPrivateKey()
    val ciphertextStream = ByteArrayOutputStream()
    cryptoHandler.encrypt(
      listOf(key),
      CryptoConstants.PLAIN_TEXT.byteInputStream(Charsets.UTF_8),
      ciphertextStream,
    )
    val plaintextStream = ByteArrayOutputStream()
    cryptoHandler.decrypt(
      key,
      CryptoConstants.KEY_PASSPHRASE,
      ciphertextStream.toByteArray().inputStream(),
      plaintextStream,
    )
    assertEquals(CryptoConstants.PLAIN_TEXT, plaintextStream.toString(Charsets.UTF_8))
  }

  @Test
  fun can_handle_filters_formats() {
    assertFalse { cryptoHandler.canHandle("example.com") }
    assertTrue { cryptoHandler.canHandle("example.com.gpg") }
    assertFalse { cryptoHandler.canHandle("example.com.asc") }
  }
}

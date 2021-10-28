/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.pgpainless.PGPainless

class PGPKeyPairTest {

  @Test
  fun testIfKeyIdIsCorrect() {
    val secretKey = PGPainless.readKeyRing().secretKeyRing(getKey()).secretKey
    val keyPair = PGPKeyPair(secretKey)

    assertEquals(CryptoConstants.KEY_ID, keyPair.getKeyId())
  }

  private fun getKey(): String = this::class.java.classLoader.getResource("private_key").readText()
}

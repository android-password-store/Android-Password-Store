/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.pgpainless.PGPainless

public class GPGKeyPairTest {

  @Test
  public fun testIfKeyIdIsCorrect() {
    val secretKey = PGPainless.readKeyRing().secretKeyRing(getKey()).secretKey
    val keyPair = PGPKeyPair(secretKey)

    assertEquals(CryptoConstants.KEY_ID, keyPair.getKeyId())
  }

  @Test
  public fun testBuildingKeyPairWithoutPrivateKey() {
    assertFailsWith<KeyPairException.PrivateKeyUnavailableException> {
      // Get public key object from private key
      val publicKey = PGPainless.readKeyRing().secretKeyRing(getKey()).publicKey

      // Create secret key ring from public key
      val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(publicKey.encoded)

      // Get secret key from key ring
      val publicSecretKey = secretKeyRing.secretKey

      // Try creating a KeyPair from public key
      val keyPair = PGPKeyPair(publicSecretKey)

      keyPair.getPrivateKey()
    }
  }

  private fun getKey(): String = this::class.java.classLoader.getResource("private_key").readText()
}

/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import androidx.test.platform.app.InstrumentationRegistry
import com.proton.Gopenpgp.crypto.Key
import dev.msfjarvis.aps.crypto.utils.CryptoConstants
import dev.msfjarvis.aps.cryptopgp.test.R
import dev.msfjarvis.aps.data.crypto.GPGKeyPair
import dev.msfjarvis.aps.data.crypto.KeyPairException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

public class GPGKeyPairTest {

  @Test
  public fun testIfKeyIdIsCorrect() {
    val gpgKey = Key(getKey())
    val keyPair = GPGKeyPair(gpgKey)

    assertEquals(CryptoConstants.KEY_ID, keyPair.getKeyId())
  }

  @Test
  public fun testBuildingKeyPairWithoutPrivateKey() {
    assertFailsWith<KeyPairException.PrivateKeyUnavailableException>(
      "GPGKeyPair does not have a private sub key"
    ) {
      // Get public key object from private key
      val gpgKey = Key(getKey()).toPublic()
      // Try creating a KeyPair from public key
      val keyPair = GPGKeyPair(gpgKey)

      keyPair.getPrivateKey()
    }
  }

  private companion object {

    fun getKey(): String =
      InstrumentationRegistry.getInstrumentation()
        .context
        .resources
        .openRawResource(R.raw.private_key)
        .readBytes()
        .decodeToString()
  }
}

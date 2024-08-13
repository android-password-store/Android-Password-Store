/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import app.passwordstore.crypto.KeyUtils.isKeyUsable
import app.passwordstore.crypto.KeyUtils.tryGetId
import app.passwordstore.crypto.KeyUtils.tryParseKeyring
import app.passwordstore.crypto.TestUtils.AllKeys
import app.passwordstore.crypto.TestUtils.getArmoredSecretKeyWithMultipleIdentities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.bouncycastle.openpgp.PGPSecretKeyRing

class KeyUtilsTest {
  @Test
  fun parseKeyWithMultipleIdentities() {
    val key = PGPKey(getArmoredSecretKeyWithMultipleIdentities())
    val keyring = tryParseKeyring(key)
    assertNotNull(keyring)
    assertIs<PGPSecretKeyRing>(keyring)
    val keyId = tryGetId(key)
    assertNotNull(keyId)
    assertIs<PGPIdentifier.KeyId>(keyId)
    assertEquals("b950ae2813841585", keyId.toString())
  }

  @Test
  fun isKeyUsable() {
    val params = AllKeys.entries.map { it to (it != AllKeys.AEAD_PUB && it != AllKeys.AEAD_SEC) }
    params.forEach { (allKeys, isUsable) ->
      val key = PGPKey(allKeys.keyMaterial)
      assertEquals(isUsable, isKeyUsable(key), "${allKeys.name} failed expectation:")
    }
  }
}

/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PGPIdentifierTest {

  @Test
  fun parseHexKeyIdWithout0xPrefix() {
    val identifier = PGPIdentifier.fromString("79E8208280490C77")
    assertNotNull(identifier)
    assertTrue { identifier is PGPIdentifier.KeyId }
  }

  @Test
  fun parseHexKeyId() {
    val identifier = PGPIdentifier.fromString("0x79E8208280490C77")
    assertNotNull(identifier)
    assertTrue { identifier is PGPIdentifier.KeyId }
  }

  @Test
  fun parseValidEmail() {
    val identifier = PGPIdentifier.fromString("john.doe@example.org")
    assertNotNull(identifier)
    assertTrue { identifier is PGPIdentifier.UserId }
  }

  @Test
  fun parseEmailWithoutTLD() {
    val identifier = PGPIdentifier.fromString("john.doe@example")
    assertNotNull(identifier)
    assertTrue { identifier is PGPIdentifier.UserId }
  }
}

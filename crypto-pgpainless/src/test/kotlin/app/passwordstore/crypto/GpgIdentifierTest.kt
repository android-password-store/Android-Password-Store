/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpgIdentifierTest {

  @Test
  fun parseHexKeyIdWithout0xPrefix() {
    val identifier = GpgIdentifier.fromString("79E8208280490C77")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.KeyId }
  }

  @Test
  fun parseHexKeyId() {
    val identifier = GpgIdentifier.fromString("0x79E8208280490C77")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.KeyId }
  }

  @Test
  fun parseValidEmail() {
    val identifier = GpgIdentifier.fromString("john.doe@example.org")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.UserId }
  }

  @Test
  fun parseEmailWithoutTLD() {
    val identifier = GpgIdentifier.fromString("john.doe@example")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.UserId }
  }
}
